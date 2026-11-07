package com.ultikits.plugins.chat.service;

import com.ultikits.plugins.chat.config.ChatConfig;
import com.ultikits.plugins.chat.i18n.CatalogueText;
import com.ultikits.plugins.chat.utils.ChatTestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AntiSpamAutoMuteTest {
    @Test
    void repeatedCapsMustBlockTheNextAcceptableMessageWhenOptedIn() throws Exception {
        ChatConfig config = new ChatConfig();
        config.setAntiSpamCooldown(0);
        config.setAutoMuteEnabled(true);
        AntiSpamService service = new AntiSpamService();
        ChatTestHelper.setField(service, "config", config);
        UltiToolsPlugin plugin = mock(UltiToolsPlugin.class);
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        ChatTestHelper.setField(service, "plugin", plugin);
        service.refreshAutoMuteSettings();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        service.checkSpam(player, "FIRST CAPS");
        service.checkSpam(player, "SECOND CAPS");
        service.checkSpam(player, "THIRD CAPS");
        assertThat(service.checkSpam(player, "ordinary acceptable text"))
                .as("the next acceptable message must be refused during the automatic mute")
                .isNotNull();
    }
}
