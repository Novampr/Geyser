/*
 * Copyright (c) 2019-2023 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.preference;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.cumulus.component.Component;
import org.geysermc.cumulus.component.DropdownComponent;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.preference.Preference;
import org.geysermc.geyser.api.preference.PreferenceKey;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.CooldownUtils;

import java.util.Arrays;
import java.util.List;

public class CooldownPreference extends Preference<CooldownUtils.CooldownType> {

    public static final PreferenceKey<CooldownUtils.CooldownType> KEY = new PreferenceKey<>("geyser:cooldown_type");

    private static final List<String> OPTIONS = Arrays.stream(CooldownUtils.CooldownType.VALUES)
        .map(CooldownUtils.CooldownType::getTranslation)
        .toList();

    public CooldownPreference(GeyserSession session) {
        super(configSetting(session));
    }

    @Override
    public boolean isModifiable(GeyserConnection connection) {
        return configSetting((GeyserSession) connection) != CooldownUtils.CooldownType.DISABLED;
    }

    @Override
    public Component component(GeyserConnection connection) {
        return DropdownComponent.of(CooldownUtils.CooldownType.OPTION_DESCRIPTION, OPTIONS, value().ordinal());
    }

    @Override
    public void onFormResponse(@NonNull Object response) throws IllegalArgumentException {
        update(CooldownUtils.CooldownType.VALUES[(int) response]);
    }

    private static CooldownUtils.CooldownType configSetting(GeyserSession session) {
        return session.getGeyser().config().gameplay().showCooldown();
    }
}
