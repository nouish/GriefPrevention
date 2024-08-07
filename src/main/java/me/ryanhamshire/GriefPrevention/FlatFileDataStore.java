/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention;

import com.google.common.io.Files;
import com.google.common.io.MoreFiles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Matcher;

//manages data stored in the file system
public class FlatFileDataStore extends DataStore
{
    private final static String claimDataFolderPath = dataLayerFolderPath + File.separator + "ClaimData";
    private final static String nextClaimIdFilePath = claimDataFolderPath + File.separator + "_nextClaimID";
    private final static String schemaVersionFilePath = dataLayerFolderPath + File.separator + "_schemaVersion";

    static boolean hasData()
    {
        File claimsDataFolder = new File(claimDataFolderPath);

        return claimsDataFolder.exists();
    }

    //initialization!
    FlatFileDataStore() throws Exception
    {
        this.initialize();
    }

    @Override
    void initialize() throws Exception
    {
        //ensure data folders exist
        boolean newDataStore = false;
        File playerDataFolder = new File(playerDataFolderPath);
        File claimDataFolder = new File(claimDataFolderPath);
        if (!playerDataFolder.exists() || !claimDataFolder.exists())
        {
            newDataStore = true;
            playerDataFolder.mkdirs();
            claimDataFolder.mkdirs();
        }

        //if there's no data yet, then anything written will use the schema implemented by this code
        if (newDataStore)
        {
            this.setSchemaVersion(DataStore.latestSchemaVersion);
        }

        //load group data into memory
        File[] files = playerDataFolder.listFiles();
        for (File file : files)
        {
            if (!file.isFile()) continue;  //avoids folders

            //all group data files start with a dollar sign.  ignoring the rest, which are player data files.
            if (!file.getName().startsWith("$")) continue;

            String groupName = file.getName().substring(1);
            if (groupName == null || groupName.isEmpty()) continue;  //defensive coding, avoid unlikely cases

            try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8);
                 BufferedReader inStream = new BufferedReader(reader))
            {
                String line = inStream.readLine();

                int groupBonusBlocks = Integer.parseInt(line);

                this.permissionToBonusBlocksMap.put(groupName, groupBonusBlocks);
            }
            catch (Exception e)
            {
                StringWriter errors = new StringWriter();
                e.printStackTrace(new PrintWriter(errors));
                GriefPrevention.AddLogEntry(errors.toString(), CustomLogEntryTypes.Exception);
            }
        }

        //load next claim number from file
        Path nextClaimIdPath = Path.of(nextClaimIdFilePath);

        try
        {
            String line = MoreFiles.asCharSource(nextClaimIdPath, StandardCharsets.UTF_8).readFirstLine();

            if (line != null)
            {
                try
                {
                    nextClaimID = Long.parseLong(line);
                }
                catch (NumberFormatException e)
                {
                    GriefPrevention.instance.getLogger().warning("File '" + nextClaimIdPath + "' contained data that is not a 64-bit integer: '" + line + "'.");
                }
            }
            else
            {
                GriefPrevention.instance.getLogger().warning("File '" + nextClaimIdPath + "' contained empty data.");
            }
        }
        catch (NoSuchFileException ignored)
        {
            // Indicates the file has not yet been created. The plugin is probably new on this server.
        }
        catch (IOException e)
        {
            GriefPrevention.instance.getLogger().log(Level.WARNING, "Unable to read next sequential claim ID from file '" + nextClaimIdPath + "'", e);
        }

        //if converting up from schema version 0, rename player data files using UUIDs instead of player namesaa
        //get a list of all the files in the claims data folder
        if (this.getSchemaVersion() == 0)
        {
            files = playerDataFolder.listFiles();
            ArrayList<String> namesToConvert = new ArrayList<>();
            for (File playerFile : files)
            {
                namesToConvert.add(playerFile.getName());
            }

            //resolve and cache as many as possible through various means
            try
            {
                UUIDFetcher fetcher = new UUIDFetcher(namesToConvert);
                fetcher.call();
            }
            catch (Exception e)
            {
                GriefPrevention.AddLogEntry("Failed to resolve a batch of names to UUIDs.  Details:" + e.getMessage());
                e.printStackTrace();
            }

            //rename files
            for (File playerFile : files)
            {
                String currentFilename = playerFile.getName();

                //if corrected casing and a record already exists using the correct casing, skip this one
                String correctedCasing = UUIDFetcher.correctedNames.get(currentFilename);
                if (correctedCasing != null && !currentFilename.equals(correctedCasing))
                {
                    File correctedCasingFile = new File(playerDataFolder, correctedCasing);
                    if (correctedCasingFile.exists())
                    {
                        continue;
                    }
                }

                //try to convert player name to UUID
                UUID playerID = null;
                try
                {
                    playerID = UUIDFetcher.getUUIDOf(currentFilename);

                    //if successful, rename the file using the UUID
                    if (playerID != null)
                    {
                        playerFile.renameTo(new File(playerDataFolder, playerID.toString()));
                    }
                }
                catch (Exception ex) { }
            }
        }

        //load claims data into memory
        //get a list of all the files in the claims data folder
        files = claimDataFolder.listFiles();

        if (this.getSchemaVersion() <= 1)
        {
            this.loadClaimData_Legacy(files);
        }
        else
        {
            this.loadClaimData(files);
        }

        super.initialize();
    }

    void loadClaimData_Legacy(File[] files) throws Exception
    {
        List<World> validWorlds = Bukkit.getServer().getWorlds();

        for (int i = 0; i < files.length; i++)
        {
            if (files[i].isFile())  //avoids folders
            {
                //skip any file starting with an underscore, to avoid special files not representing land claims
                if (files[i].getName().startsWith("_")) continue;

                //the filename is the claim ID.  try to parse it
                long claimID;

                try
                {
                    claimID = Long.parseLong(files[i].getName());
                }

                //because some older versions used a different file name pattern before claim IDs were introduced,
                //those files need to be "converted" by renaming them to a unique ID
                catch (Exception e)
                {
                    claimID = this.nextClaimID;
                    this.incrementNextClaimID();
                    File newFile = new File(claimDataFolderPath, String.valueOf(this.nextClaimID));
                    files[i].renameTo(newFile);
                    files[i] = newFile;
                }

                String lesserCornerString = "";
                try (FileReader reader = new FileReader(files[i], StandardCharsets.UTF_8);
                     BufferedReader inStream = new BufferedReader(reader))
                {
                    Claim topLevelClaim = null;
                    String line = inStream.readLine();

                    while (line != null)
                    {
                        //skip any SUB:### lines from previous versions
                        if (line.toLowerCase().startsWith("sub:"))
                        {
                            line = inStream.readLine();
                        }

                        //skip any UUID lines from previous versions
                        Matcher match = uuidpattern.matcher(line.trim());
                        if (match.find())
                        {
                            line = inStream.readLine();
                        }

                        //first line is lesser boundary corner location
                        lesserCornerString = line;
                        Location lesserBoundaryCorner = this.locationFromString(lesserCornerString, validWorlds);

                        //second line is greater boundary corner location
                        line = inStream.readLine();
                        Location greaterBoundaryCorner = this.locationFromString(line, validWorlds);

                        //third line is owner name
                        line = inStream.readLine();
                        String ownerName = line;
                        UUID ownerID = null;
                        if (ownerName.isEmpty() || ownerName.startsWith("--"))
                        {
                            ownerID = null;  //administrative land claim or subdivision
                        }
                        else if (this.getSchemaVersion() == 0)
                        {
                            try
                            {
                                ownerID = UUIDFetcher.getUUIDOf(ownerName);
                            }
                            catch (Exception ex)
                            {
                                GriefPrevention.AddLogEntry("Couldn't resolve this name to a UUID: " + ownerName + ".");
                                GriefPrevention.AddLogEntry("  Converted land claim to administrative @ " + lesserBoundaryCorner.toString());
                            }
                        }
                        else
                        {
                            try
                            {
                                ownerID = UUID.fromString(ownerName);
                            }
                            catch (Exception ex)
                            {
                                GriefPrevention.AddLogEntry("Error - this is not a valid UUID: " + ownerName + ".");
                                GriefPrevention.AddLogEntry("  Converted land claim to administrative @ " + lesserBoundaryCorner.toString());
                            }
                        }

                        //fourth line is list of builders
                        line = inStream.readLine();
                        List<String> builderNames = Arrays.asList(line.split(";"));
                        builderNames = this.convertNameListToUUIDList(builderNames);

                        //fifth line is list of players who can access containers
                        line = inStream.readLine();
                        List<String> containerNames = Arrays.asList(line.split(";"));
                        containerNames = this.convertNameListToUUIDList(containerNames);

                        //sixth line is list of players who can use buttons and switches
                        line = inStream.readLine();
                        List<String> accessorNames = Arrays.asList(line.split(";"));
                        accessorNames = this.convertNameListToUUIDList(accessorNames);

                        //seventh line is list of players who can grant permissions
                        line = inStream.readLine();
                        if (line == null) line = "";
                        List<String> managerNames = Arrays.asList(line.split(";"));
                        managerNames = this.convertNameListToUUIDList(managerNames);

                        //skip any remaining extra lines, until the "===" string, indicating the end of this claim or subdivision
                        line = inStream.readLine();
                        while (line != null && !line.contains("==="))
                            line = inStream.readLine();

                        //build a claim instance from those data
                        //if this is the first claim loaded from this file, it's the top level claim
                        if (topLevelClaim == null)
                        {
                            //instantiate
                            topLevelClaim = new Claim(lesserBoundaryCorner, greaterBoundaryCorner, ownerID, builderNames, containerNames, accessorNames, managerNames, claimID);

                            topLevelClaim.modifiedDate = new Date(files[i].lastModified());
                            this.addClaim(topLevelClaim, false);
                        }

                        //otherwise there's already a top level claim, so this must be a subdivision of that top level claim
                        else
                        {
                            Claim subdivision = new Claim(lesserBoundaryCorner, greaterBoundaryCorner, null, builderNames, containerNames, accessorNames, managerNames, null);

                            subdivision.modifiedDate = new Date(files[i].lastModified());
                            subdivision.parent = topLevelClaim;
                            topLevelClaim.children.add(subdivision);
                            subdivision.inDataStore = true;
                        }

                        //move up to the first line in the next subdivision
                        line = inStream.readLine();
                    }
                }

                //if there's any problem with the file's content, log an error message and skip it
                catch (Exception e)
                {
                    if (e.getMessage() != null && e.getMessage().contains("World not found"))
                    {
                        GriefPrevention.AddLogEntry("Failed to load a claim " + files[i].getName() + " because its world isn't loaded (yet?).  Please delete the claim file or contact the GriefPrevention developer with information about which plugin(s) you're using to load or create worlds.  " + lesserCornerString);
                    }
                    else
                    {
                        StringWriter errors = new StringWriter();
                        e.printStackTrace(new PrintWriter(errors));
                        GriefPrevention.AddLogEntry("Failed to load claim " + files[i].getName() + ". This usually occurs when your server runs out of storage space, causing any file saves to corrupt. Fix or delete the file found in GriefPreventionData/ClaimData/" + files[i].getName(), CustomLogEntryTypes.Debug, false);
                        GriefPrevention.AddLogEntry(files[i].getName() + " " + errors.toString(), CustomLogEntryTypes.Exception);
                    }
                }
            }
        }
    }

    void loadClaimData(File[] files) throws Exception
    {
        ConcurrentHashMap<Claim, Long> orphans = new ConcurrentHashMap<>();
        for (int i = 0; i < files.length; i++)
        {
            if (files[i].isFile())  //avoids folders
            {
                //skip any file starting with an underscore, to avoid special files not representing land claims
                if (files[i].getName().startsWith("_")) continue;

                //delete any which don't end in .yml
                if (!files[i].getName().endsWith(".yml"))
                {
                    files[i].delete();
                    continue;
                }

                //the filename is the claim ID.  try to parse it
                long claimID;

                try
                {
                    claimID = Long.parseLong(files[i].getName().split("\\.")[0]);
                }

                //because some older versions used a different file name pattern before claim IDs were introduced,
                //those files need to be "converted" by renaming them to a unique ID
                catch (Exception e)
                {
                    claimID = this.nextClaimID;
                    this.incrementNextClaimID();
                    File newFile = new File(claimDataFolderPath, String.valueOf(this.nextClaimID) + ".yml");
                    files[i].renameTo(newFile);
                    files[i] = newFile;
                }

                try
                {
                    ArrayList<Long> out_parentID = new ArrayList<>();  //hacky output parameter
                    Claim claim = this.loadClaim(files[i], out_parentID, claimID);
                    if (out_parentID.isEmpty() || out_parentID.get(0) == -1)
                    {
                        this.addClaim(claim, false);
                    }
                    else
                    {
                        orphans.put(claim, out_parentID.get(0));
                    }
                }

                //if there's any problem with the file's content, log an error message and skip it
                catch (Exception e)
                {
                    if (e.getMessage() != null && e.getMessage().contains("World not found"))
                    {
                        GriefPrevention.AddLogEntry("Failed to load a claim (ID:" + claimID + ") because its world isn't loaded (yet?).  If this is not expected, delete this claim.");
                    }
                    else
                    {
                        StringWriter errors = new StringWriter();
                        e.printStackTrace(new PrintWriter(errors));
                        GriefPrevention.AddLogEntry(files[i].getName() + " " + errors.toString(), CustomLogEntryTypes.Exception);
                    }
                }
            }
        }

        //link children to parents
        for (Claim child : orphans.keySet())
        {
            Claim parent = this.getClaim(orphans.get(child));
            if (parent != null)
            {
                child.parent = parent;
                this.addClaim(child, false);
            }
        }
    }

    Claim loadClaim(File file, ArrayList<Long> out_parentID, long claimID) throws IOException, InvalidConfigurationException, Exception
    {
        List<String> lines = Files.readLines(file, StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder();
        for (String line : lines)
        {
            builder.append(line).append('\n');
        }

        return this.loadClaim(builder.toString(), out_parentID, file.lastModified(), claimID, Bukkit.getServer().getWorlds());
    }

    Claim loadClaim(String input, ArrayList<Long> out_parentID, long lastModifiedDate, long claimID, List<World> validWorlds) throws InvalidConfigurationException, Exception
    {
        Claim claim = null;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(input);

        //boundaries
        Location lesserBoundaryCorner = this.locationFromString(yaml.getString("Lesser Boundary Corner"), validWorlds);
        Location greaterBoundaryCorner = this.locationFromString(yaml.getString("Greater Boundary Corner"), validWorlds);

        //owner
        String ownerIdentifier = yaml.getString("Owner");
        UUID ownerID = null;
        if (!ownerIdentifier.isEmpty())
        {
            try
            {
                ownerID = UUID.fromString(ownerIdentifier);
            }
            catch (Exception ex)
            {
                GriefPrevention.AddLogEntry("Error - this is not a valid UUID: " + ownerIdentifier + ".");
                GriefPrevention.AddLogEntry("  Converted land claim to administrative @ " + lesserBoundaryCorner.toString());
            }
        }

        String name = yaml.getString("Name");

        List<String> builders = yaml.getStringList("Builders");

        List<String> containers = yaml.getStringList("Containers");

        List<String> accessors = yaml.getStringList("Accessors");

        List<String> managers = yaml.getStringList("Managers");

        boolean inheritNothing = yaml.getBoolean("inheritNothing");

        out_parentID.add(yaml.getLong("Parent Claim ID", -1L));

        //instantiate
        claim = new Claim(lesserBoundaryCorner, greaterBoundaryCorner, ownerID, builders, containers, accessors, managers, inheritNothing, claimID);
        claim.modifiedDate = new Date(lastModifiedDate);
        claim.id = claimID;
        claim.setName(name);

        return claim;
    }

    String getYamlForClaim(Claim claim)
    {
        YamlConfiguration yaml = new YamlConfiguration();

        //boundaries
        yaml.set("Lesser Boundary Corner", this.locationToString(claim.lesserBoundaryCorner));
        yaml.set("Greater Boundary Corner", this.locationToString(claim.greaterBoundaryCorner));

        //owner
        String ownerID = "";
        if (claim.ownerID != null) ownerID = claim.ownerID.toString();
        yaml.set("Owner", ownerID);

        yaml.set("Name", claim.getName().orElse(null));

        ArrayList<String> builders = new ArrayList<>();
        ArrayList<String> containers = new ArrayList<>();
        ArrayList<String> accessors = new ArrayList<>();
        ArrayList<String> managers = new ArrayList<>();
        claim.getPermissions(builders, containers, accessors, managers);

        yaml.set("Builders", builders);
        yaml.set("Containers", containers);
        yaml.set("Accessors", accessors);
        yaml.set("Managers", managers);

        Long parentID = -1L;
        if (claim.parent != null)
        {
            parentID = claim.parent.id;
        }

        yaml.set("Parent Claim ID", parentID);

        yaml.set("inheritNothing", claim.getSubclaimRestrictions());

        return yaml.saveToString();
    }

    @Override
    synchronized void writeClaimToStorage(Claim claim)
    {
        String yaml = getYamlForClaim(claim);

        Path claimDirs = Path.of(claimDataFolderPath);
        Path claimDataPath = claimDirs.resolve(claim.id + ".yml");
        Path tempDataPath;

        try
        {
            tempDataPath = java.nio.file.Files.createTempFile(claimDirs, "claim", ".tmp");
        }
        catch (IOException e)
        {
            GriefPrevention.instance.getLogger().log(Level.WARNING, String.format("Unable to create temp file to save claim %d. Unable to proceed saving, meaning any changes may be lost.", claim.id), e);
            return;
        }

        try
        {
            try
            {
                MoreFiles.asCharSink(tempDataPath, StandardCharsets.UTF_8).write(yaml);
            }
            catch (IOException e)
            {
                throw new IOException("Unable to write YAML to file '" + tempDataPath + "'", e);
            }

            try
            {
                java.nio.file.Files.move(tempDataPath, claimDataPath, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException e)
            {
                throw new IOException("Unable to move temporary file '" + tempDataPath + "' to '" + claimDataPath + "'", e);
            }

            if (GriefPrevention.instance.config_logs_debugEnabled)
            {
                GriefPrevention.instance.getLogger().info(String.format("Successfully saved claim %s to '%s'.", claim.id, claimDataPath));
            }
        }
        catch (IOException e)
        {
            GriefPrevention.instance.getLogger().log(Level.WARNING, "Failed to save data for claim " + claim.id + ".", e);
        }
        finally
        {
            try
            {
                // Clean up tmp file - the file may have been created, even if the operation couldn't be completed.
                java.nio.file.Files.deleteIfExists(tempDataPath);
            }
            catch (IOException ignored)
            {
                // Not very important
            }
        }
    }

    //deletes a claim from the file system
    @Override
    synchronized void deleteClaimFromSecondaryStorage(Claim claim)
    {
        String claimID = String.valueOf(claim.id);

        //remove from disk
        File claimFile = new File(claimDataFolderPath, claimID + ".yml");
        if (claimFile.exists() && !claimFile.delete())
        {
            GriefPrevention.AddLogEntry("Error: Unable to delete claim file \"" + claimFile.getAbsolutePath() + "\".");
        }
    }

    @Override
    synchronized PlayerData getPlayerDataFromStorage(UUID playerID)
    {
        File playerFile = new File(playerDataFolderPath, playerID.toString());

        PlayerData playerData = new PlayerData();
        playerData.playerID = playerID;

        //if it exists as a file, read the file
        if (playerFile.exists())
        {
            boolean needRetry = false;
            int retriesRemaining = 5;
            Exception latestException = null;
            do
            {
                try
                {
                    needRetry = false;

                    //read the file content and immediately close it
                    List<String> lines = Files.readLines(playerFile, StandardCharsets.UTF_8);
                    Iterator<String> iterator = lines.iterator();


                    iterator.next();
                    //first line is last login timestamp //RoboMWM - not using this anymore
//
//    				//convert that to a date and store it
//    				DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
//    				try
//    				{
//    					playerData.setLastLogin(dateFormat.parse(lastLoginTimestampString));
//    				}
//    				catch(ParseException parseException)
//    				{
//    					GriefPrevention.AddLogEntry("Unable to load last login for \"" + playerFile.getName() + "\".");
//    					playerData.setLastLogin(null);
//    				}

                    //second line is accrued claim blocks
                    String accruedBlocksString = iterator.next();

                    //convert that to a number and store it
                    playerData.setAccruedClaimBlocks(Integer.parseInt(accruedBlocksString));

                    //third line is any bonus claim blocks granted by administrators
                    String bonusBlocksString = iterator.next();

                    //convert that to a number and store it
                    playerData.setBonusClaimBlocks(Integer.parseInt(bonusBlocksString));

                    //fourth line is a double-semicolon-delimited list of claims, which is currently ignored
                    //String claimsString = inStream.readLine();
                    //iterator.next();
                }

                //if there's any problem with the file's content, retry up to 5 times with 5 milliseconds between
                catch (Exception e)
                {
                    latestException = e;
                    needRetry = true;
                    retriesRemaining--;
                }

                try
                {
                    if (needRetry) Thread.sleep(5);
                }
                catch (InterruptedException exception) {}

            } while (needRetry && retriesRemaining >= 0);

            //if last attempt failed, log information about the problem
            if (needRetry)
            {
                StringWriter errors = new StringWriter();
                latestException.printStackTrace(new PrintWriter(errors));
                GriefPrevention.AddLogEntry("Failed to load PlayerData for " + playerID + ". This usually occurs when your server runs out of storage space, causing any file saves to corrupt. Fix or delete the file in GriefPrevetionData/PlayerData/" + playerID, CustomLogEntryTypes.Debug, false);
                GriefPrevention.AddLogEntry(playerID + " " + errors.toString(), CustomLogEntryTypes.Exception);
            }
        }

        return playerData;
    }

    //saves changes to player data.  MUST be called after you're done making changes, otherwise a reload will lose them
    @Override
    public void overrideSavePlayerData(UUID playerID, PlayerData playerData)
    {
        //never save data for the "administrative" account.  null for claim owner ID indicates administrative account
        if (playerID == null) return;

        StringBuilder sbuf = new StringBuilder();
        //first line is last login timestamp //RoboMWM - no longer storing/using
        //if(playerData.getLastLogin() == null) playerData.setLastLogin(new Date());
        //DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
        //fileContent.append(dateFormat.format(playerData.getLastLogin()));
        sbuf.append("\n");

        //second line is accrued claim blocks
        sbuf.append(String.valueOf(playerData.getAccruedClaimBlocks()));
        sbuf.append("\n");

        //third line is bonus claim blocks
        sbuf.append(String.valueOf(playerData.getBonusClaimBlocks()));
        sbuf.append("\n");

        //fourth line is blank
        sbuf.append("\n");

        Path playerDataDir = Path.of(playerDataFolderPath);
        Path playerDataPath = playerDataDir.resolve(playerID.toString());
        Path tempPlayerDataPath;

        try
        {
            tempPlayerDataPath = java.nio.file.Files.createTempFile(playerDataDir, "playerdata", ".tmp");
        }
        catch (IOException e)
        {
            GriefPrevention.instance.getLogger().log(Level.WARNING, String.format("Unable to create temp file to save playerdata for %s. Unable to proceed save, meaning any changes may be lost.", playerID), e);
            return;
        }

        try
        {
            //write data to file
            try
            {
                MoreFiles.asCharSink(tempPlayerDataPath, StandardCharsets.UTF_8).write(sbuf.toString());
            }
            catch (IOException e)
            {
                throw new IOException("Unable to write playerdata to temp file '" + tempPlayerDataPath + "'", e);
            }

            try
            {
                java.nio.file.Files.move(tempPlayerDataPath, playerDataPath, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException e)
            {
                throw new IOException("Unable to move temporary file '" + tempPlayerDataPath + "' to '" + playerDataPath + "'", e);
            }

            if (GriefPrevention.instance.config_logs_debugEnabled)
            {
                GriefPrevention.instance.getLogger().info(String.format("Successfully saved playerdata for %s to '%s'.", playerID, playerDataPath));
            }
        }

        //if any problem, log it
        catch (IOException e)
        {
            GriefPrevention.instance.getLogger().log(Level.WARNING, "Failed to save playerdata for " + playerID + ":", e);
        }
        finally
        {
            try
            {
                // Clean up tmp file - the file may have been created, even if the operation couldn't be completed.
                java.nio.file.Files.deleteIfExists(tempPlayerDataPath);
            }
            catch (IOException ignored)
            {
                // Not very important
            }
        }
    }

    @Override
    synchronized void incrementNextClaimID()
    {
        //increment in memory
        this.nextClaimID++;

        Path nextClaimIdPath = Path.of(nextClaimIdFilePath);
        Path tempNextClaimIdPath;

        try
        {
            tempNextClaimIdPath = java.nio.file.Files.createTempFile("nextclaimid", ".tmp");
        }
        catch (IOException e)
        {
            GriefPrevention.instance.getLogger().log(Level.WARNING, "Unable to create temp file for nextclaimid. The plugin may try to correct this next time the server starts, but this may lead to claims being overwritten.", e);
            return;
        }

        try
        {
            try
            {
                MoreFiles.asCharSink(tempNextClaimIdPath, StandardCharsets.UTF_8).write(String.valueOf(nextClaimID));
            }
            catch (IOException e)
            {
                throw new IOException("Unable to write sequential claim id to '" + tempNextClaimIdPath + "'", e);
            }

            try
            {
                java.nio.file.Files.move(tempNextClaimIdPath, nextClaimIdPath, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException e)
            {
                throw new IOException("Unable to move temporary file '" + tempNextClaimIdPath + "' to '" + nextClaimIdPath + "'", e);
            }
        }
        catch (IOException e)
        {
            GriefPrevention.instance.getLogger().log(Level.WARNING, "Failed to save sequential claim id. The plugin may try to correct this next time the server starts, but this may lead to claims being overwritten.", e);
        }
        finally
        {
            try
            {
                // Clean up tmp file - the file may have been created, even if the operation couldn't be completed.
                java.nio.file.Files.deleteIfExists(tempNextClaimIdPath);
            }
            catch (IOException ignored)
            {
                // Not very important
            }
        }
    }

    //grants a group (players with a specific permission) bonus claim blocks as long as they're still members of the group
    @Override
    synchronized void saveGroupBonusBlocks(String groupName, int currentValue)
    {
        //write changes to file to ensure they don't get lost
        File groupDataFile = new File(playerDataFolderPath, "$" + groupName);
        try (FileWriter writer = new FileWriter(groupDataFile, StandardCharsets.UTF_8);
             BufferedWriter outStream = new BufferedWriter(writer))
        {
            //first line is number of bonus blocks
            outStream.write(String.valueOf(currentValue));
            outStream.newLine();
        }

        //if any problem, log it
        catch (Exception e)
        {
            GriefPrevention.AddLogEntry("Unexpected exception saving data for group \"" + groupName + "\": " + e.getMessage());
        }
    }

    synchronized void migrateData(DatabaseDataStore databaseStore)
    {
        //migrate claims
        for (Claim claim : this.claims)
        {
            databaseStore.addClaim(claim, true);
            for (Claim child : claim.children)
            {
                databaseStore.addClaim(child, true);
            }
        }

        //migrate groups
        for (Map.Entry<String, Integer> groupEntry : this.permissionToBonusBlocksMap.entrySet())
        {
            databaseStore.saveGroupBonusBlocks(groupEntry.getKey(), groupEntry.getValue());
        }

        //migrate players
        File playerDataFolder = new File(playerDataFolderPath);
        File[] files = playerDataFolder.listFiles();
        for (File file : files)
        {
            if (!file.isFile()) continue;  //avoids folders
            if (file.isHidden()) continue; //avoid hidden files, which are likely not created by GriefPrevention

            //all group data files start with a dollar sign.  ignoring those, already handled above
            if (file.getName().startsWith("$")) continue;

            //ignore special files
            if (file.getName().startsWith("_")) continue;
            if (file.getName().endsWith(".ignore")) continue;

            UUID playerID = UUID.fromString(file.getName());
            databaseStore.savePlayerData(playerID, this.getPlayerData(playerID));
            this.clearCachedPlayerData(playerID);
        }

        //migrate next claim ID
        if (this.nextClaimID > databaseStore.nextClaimID)
        {
            databaseStore.setNextClaimID(this.nextClaimID);
        }

        //rename player and claim data folders so the migration won't run again
        int i = 0;
        File claimsBackupFolder;
        File playersBackupFolder;
        do
        {
            String claimsFolderBackupPath = claimDataFolderPath;
            if (i > 0) claimsFolderBackupPath += String.valueOf(i);
            claimsBackupFolder = new File(claimsFolderBackupPath);

            String playersFolderBackupPath = playerDataFolderPath;
            if (i > 0) playersFolderBackupPath += String.valueOf(i);
            playersBackupFolder = new File(playersFolderBackupPath);
            i++;
        } while (claimsBackupFolder.exists() || playersBackupFolder.exists());

        File claimsFolder = new File(claimDataFolderPath);
        File playersFolder = new File(playerDataFolderPath);

        claimsFolder.renameTo(claimsBackupFolder);
        playersFolder.renameTo(playersBackupFolder);

        GriefPrevention.AddLogEntry("Backed your file system data up to " + claimsBackupFolder.getName() + " and " + playersBackupFolder.getName() + ".");
        GriefPrevention.AddLogEntry("If your migration encountered any problems, you can restore those data with a quick copy/paste.");
        GriefPrevention.AddLogEntry("When you're satisfied that all your data have been safely migrated, consider deleting those folders.");
    }

    @Override
    synchronized void close() { }

    @Override
    int getSchemaVersionFromStorage()
    {
        File schemaVersionFile = new File(schemaVersionFilePath);
        if (schemaVersionFile.exists())
        {
            int schemaVersion = 0;
            try (FileReader reader = new FileReader(schemaVersionFile, StandardCharsets.UTF_8);
                 BufferedReader inStream = new BufferedReader(reader))
            {
                //read the version number
                String line = inStream.readLine();

                //try to parse into an int value
                schemaVersion = Integer.parseInt(line);
            }
            catch (Exception e) { }

            return schemaVersion;
        }
        else
        {
            this.updateSchemaVersionInStorage(0);
            return 0;
        }
    }

    @Override
    void updateSchemaVersionInStorage(int versionToSet)
    {
        try (FileWriter writer = new FileWriter(schemaVersionFilePath, StandardCharsets.UTF_8);
             BufferedWriter outStream = new BufferedWriter(writer))
        {
            //open the file and write the new value
            outStream.write(String.valueOf(versionToSet));
        }

        //if any problem, log it
        catch (Exception e)
        {
            GriefPrevention.AddLogEntry("Unexpected exception saving schema version: " + e.getMessage());
        }
    }
}
