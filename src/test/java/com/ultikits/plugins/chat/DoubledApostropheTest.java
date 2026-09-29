package com.ultikits.plugins.chat;

import com.ultikits.plugins.chat.i18n.CatalogueText;
import com.ultikits.ultitools.entities.Language;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Language entries written with doubled apostrophes ({@code ''{0}''}, {@code don''t}) reach the
 * player with both characters: nothing formats them with {@code MessageFormat}. The operator's
 * extracted language file keeps an older entry's text, so correcting the jar alone would not reach
 * an upgraded server: the text is un-doubled where the module reads it, and the jar is corrected too
 * (UltiKits/UltiChat#37, maintainer decision 2026-09-27).
 */
@DisplayName("A doubled apostrophe is shown as one (UltiKits/UltiChat#37)")
class DoubledApostropheTest {

    @Test
    @DisplayName("an upgraded server's language file with ''{0}'' and don''t shows one apostrophe each")
    void upgradedLanguageFileShowsOneApostrophe() {
        Language upgraded = mock(Language.class);
        when(upgraded.getLocalizedText("autoreply_added")).thenReturn("&aAuto-reply rule ''{0}'' added.");
        when(upgraded.getLocalizedText("channel_no_permission")).thenReturn("&cYou don''t have permission for channel {0}.");
        UltiChat plugin = mock(UltiChat.class);
        when(plugin.getLanguage()).thenReturn(upgraded);
        when(plugin.i18n(anyString())).thenCallRealMethod();

        assertThat(plugin.i18n("autoreply_added")).isEqualTo("&aAuto-reply rule '{0}' added.");
        assertThat(plugin.i18n("channel_no_permission")).isEqualTo("&cYou don't have permission for channel {0}.");
    }

    @Test
    @DisplayName("the jar's own catalogues write every apostrophe once")
    void jarCataloguesHaveNoDoubledApostrophe() {
        for (String language : new String[] {"en", "zh"}) {
            Map<String, String> entries = CatalogueText.entries(language);
            assertThat(entries).as("control: the %s catalogue was read", language).containsKey("autoreply_added");
            assertThat(entries.values()).as(language).noneMatch(text -> text.contains("''"));
        }
    }
}
