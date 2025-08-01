/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.command.defaults;

import com.fasterxml.jackson.databind.JsonNode;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.util.MinecraftVersion;
import org.geysermc.geyser.api.util.PlatformType;
import org.geysermc.geyser.api.util.TriState;
import org.geysermc.geyser.command.GeyserCommand;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.text.ChatColor;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.WebUtils;
import org.incendo.cloud.context.CommandContext;

import java.io.IOException;
import java.util.List;

public class VersionCommand extends GeyserCommand {

    private static final String SUPPORTED_BEDROCK_RANGE;
    private static final String SUPPORTED_JAVA_RANGE;

    static {
        List<MinecraftVersion> bedrockVersions = GameProtocol.SUPPORTED_BEDROCK_VERSIONS;
        if (bedrockVersions.size() > 1) {
            SUPPORTED_BEDROCK_RANGE = bedrockVersions.get(0).versionString() + " - " + bedrockVersions.get(bedrockVersions.size() - 1).versionString();
        } else {
            SUPPORTED_BEDROCK_RANGE = bedrockVersions.get(0).versionString();
        }

        List<String> javaVersions = GameProtocol.getJavaVersions();
        if (javaVersions.size() > 1) {
            SUPPORTED_JAVA_RANGE = javaVersions.get(0) + " - " + javaVersions.get(javaVersions.size() - 1);
        } else {
            SUPPORTED_JAVA_RANGE = javaVersions.get(0);
        }
    }

    private final GeyserImpl geyser;

    public VersionCommand(GeyserImpl geyser, String name, String description, String permission) {
        super(name, description, permission, TriState.NOT_SET);
        this.geyser = geyser;
    }

    @Override
    public void execute(CommandContext<GeyserCommandSource> context) {
        GeyserCommandSource source = context.sender();

        source.sendMessage(GeyserLocale.getPlayerLocaleString("geyser.commands.version.version", source.locale(),
                GeyserImpl.NAME, GeyserImpl.VERSION, SUPPORTED_JAVA_RANGE, SUPPORTED_BEDROCK_RANGE));

        // Disable update checking in dev mode and for players in Geyser Standalone
        if (!GeyserImpl.getInstance().isProductionEnvironment() || (!source.isConsole() && geyser.getPlatformType() == PlatformType.STANDALONE)) {
            return;
        }

        if (GeyserImpl.IS_DEV) {
            source.sendMessage(GeyserLocale.getPlayerLocaleString("geyser.core.dev_build", source.locale(), "https://discord.gg/geysermc"));
            return;
        }

        source.sendMessage(GeyserLocale.getPlayerLocaleString("geyser.commands.version.checking", source.locale()));
        try {
            int buildNumber = this.geyser.buildNumber();
            JsonNode response = WebUtils.getJson("https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest");
            int latestBuildNumber = response.get("build").asInt();

            if (latestBuildNumber == buildNumber) {
                source.sendMessage(GeyserLocale.getPlayerLocaleString("geyser.commands.version.no_updates", source.locale()));
                return;
            }

            source.sendMessage(GeyserLocale.getPlayerLocaleString(
                    "geyser.commands.version.outdated",
                    source.locale(), (latestBuildNumber - buildNumber), "https://geysermc.org/download"
            ));
        } catch (IOException e) {
            GeyserImpl.getInstance().getLogger().error(GeyserLocale.getLocaleStringLog("geyser.commands.version.failed"), e);
            source.sendMessage(ChatColor.RED + GeyserLocale.getPlayerLocaleString("geyser.commands.version.failed", source.locale()));
        }
    }
}
