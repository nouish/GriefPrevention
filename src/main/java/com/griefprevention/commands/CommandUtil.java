package com.griefprevention.commands;

import com.google.common.base.CharMatcher;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

final class CommandUtil
{
    static CompletableFuture<Suggestions> suggest(Iterable<String> iterable, SuggestionsBuilder builder)
    {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (String suggestion : iterable)
        {
            if (matchesSubStr(remaining, suggestion.toLowerCase(Locale.ROOT)))
            {
                builder.suggest(suggestion);
            }
        }

        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggest(Stream<String> stream, SuggestionsBuilder builder)
    {
        Objects.requireNonNull(builder);
        String remainingAsLowerCase = builder.getRemaining().toLowerCase(Locale.ROOT);
        Stream<String> subStream = stream.filter(suggestion ->
                matchesSubStr(remainingAsLowerCase, suggestion.toLowerCase(Locale.ROOT)));
        subStream.forEach(builder::suggest);
        return builder.buildFuture();
    }

    private final static CharMatcher MATCH_SPLITTER = CharMatcher.anyOf("._/");

    private static boolean matchesSubStr(String option, String written)
    {
        int index;

        for (int i = 0; !written.startsWith(option, i); i = index + 1)
        {
            index = MATCH_SPLITTER.indexIn(written, i);
            if (index < 0)
            {
                return false;
            }
        }

        return true;
    }

    private CommandUtil() {}
}
