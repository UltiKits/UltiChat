package com.ultikits.plugins.chat.config;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigEntity("config/channels.yml")
public class ChannelConfig extends AbstractConfigEntity {

    @ConfigEntry(path = "channels.enabled", comment = "Enable channel system / 启用频道系统")
    private boolean enabled = true;

    @ConfigEntry(path = "channels.default-channel", comment = "Default channel for new players / 默认频道")
    private String defaultChannel = "global";

    /**
     * Channel definitions. A channel may set {@code format:} to replace the chat line for its
     * members, with {@code {display}} for the channel's display name; without one the line is the
     * global {@code chat.format} with the display name in front. Either way, a line format is applied
     * only while {@code channels.enabled} and {@code chat.format-enabled} (in {@code chat.yml}) are
     * both true. The shipped channels set none (UltiKits/UltiChat#16).
     */
    @ConfigEntry(path = "channels.channels", comment = "Channel definitions / 频道定义")
    private Map<String, Map<String, Object>> channels = new HashMap<String, Map<String, Object>>() {{
        HashMap<String, Object> global = new HashMap<>();
        global.put("display-name", "&f[Global]");
        global.put("permission", "");
        global.put("range", -1);
        global.put("cross-world", true);
        put("global", global);

        HashMap<String, Object> local = new HashMap<>();
        local.put("display-name", "&a[Local]");
        local.put("permission", "");
        local.put("range", 100);
        local.put("cross-world", false);
        put("local", local);

        HashMap<String, Object> staff = new HashMap<>();
        staff.put("display-name", "&c[Staff]");
        staff.put("permission", "ultichat.channel.staff");
        staff.put("range", -1);
        staff.put("cross-world", true);
        put("staff", staff);
    }};

    public ChannelConfig() {
        super("config/channels.yml");
    }
}
