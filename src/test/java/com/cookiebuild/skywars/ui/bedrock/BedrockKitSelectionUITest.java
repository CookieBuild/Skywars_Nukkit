package com.cookiebuild.skywars.ui.bedrock;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.cookiebuild.skywars.kit.SkyWarsKit;
import com.cookiebuild.skywars.ui.KitMenuSnapshot;
import com.cookiebuild.cookiedough.ui.BedrockFormImages;
import com.cookiebuild.cookiedough.ui.BedrockMenuSessionRegistry;

class BedrockKitSelectionUITest {
    @Test
    void everyKitDetailUsesAResourcePackImage() {
        for (SkyWarsKit kit : SkyWarsKit.values()) {
            String imageId = BedrockKitSelectionUI.imageId(new KitMenuSnapshot.Entry(kit, false, false));
            assertTrue(imageId.equals("kits/skywars/" + kit.key()));
            assertTrue(BedrockFormImages.isKnown(imageId));
        }
    }

    @Test
    void rendererUsesTheSharedSingleUseNonceRegistry() throws Exception {
        assertTrue(BedrockKitSelectionUI.class.getDeclaredField("sessions").getType()
                == BedrockMenuSessionRegistry.class);
    }
}
