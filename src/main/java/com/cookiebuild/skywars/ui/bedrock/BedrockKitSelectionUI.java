package com.cookiebuild.skywars.ui.bedrock;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import com.cookiebuild.skywars.SkyWars;
import com.cookiebuild.skywars.ui.KitMenuSnapshot;
import com.cookiebuild.cookiedough.ui.BedrockFormImages;

/** Native Bedrock selector/shop mirroring the Java inventory workflow. */
public final class BedrockKitSelectionUI {
    private final BiConsumer<Player, KitMenuSnapshot.Entry> selectionHandler;
    private final Consumer<Player> reopenHandler;

    public BedrockKitSelectionUI(BiConsumer<Player, KitMenuSnapshot.Entry> selectionHandler,
            Consumer<Player> reopenHandler) {
        this.selectionHandler = selectionHandler;
        this.reopenHandler = reopenHandler;
    }

    public void open(Player player, KitMenuSnapshot snapshot) {
        SimpleForm.Builder builder = SimpleForm.builder()
                .title("§l§6" + SkyWars.message(player, "skywars.kit.catalog"))
                .content("§9§l" + SkyWars.message(player, "skywars.kit.level", snapshot.level())
                        + "  §6§l" + SkyWars.message(player, "skywars.kit.coins", snapshot.coins())
                        + "\n§a§l" + SkyWars.message(player, "skywars.kit.xp", snapshot.experience(),
                                snapshot.nextLevelExperience())
                        + "\n\n§7" + SkyWars.message(player, "skywars.kit.bedrock.help"));
        List<KitMenuSnapshot.Entry> entries = snapshot.entries();
        for (KitMenuSnapshot.Entry entry : entries) {
            String prefix = entry.selected() ? "§6§l★ " : entry.unlocked() ? "§a§l✓ " : "§e§l🏪 ";
            String suffix = "\n§7" + SkyWars.message(player,
                    "skywars.kit." + entry.kit().key() + ".description");
            BedrockFormImages.button(builder, prefix + "§f§l" + entry.kit().displayName() + suffix,
                    imageId(entry));
        }
        BedrockFormImages.button(builder, "§c§l" + SkyWars.message(player, "skywars.ui.close"),
                "actions/close");
        builder.validResultHandler(response -> {
            int index = response.getClickedButtonId();
            if (index < 0 || index >= entries.size()) return;
            openDetail(player, entries.get(index));
        });
        send(player, builder.build());
    }

    private void openDetail(Player player, KitMenuSnapshot.Entry entry) {
        String status = entry.selected() ? SkyWars.message(player, "skywars.kit.action.selected")
                : entry.unlocked() ? SkyWars.message(player, "skywars.kit.action.select")
                : SkyWars.message(player, "skywars.kit.action.purchase", entry.kit().price());
        SimpleForm.Builder form = SimpleForm.builder()
                .title("§l§6" + entry.kit().displayName())
                .content(SkyWars.message(player, "skywars.kit." + entry.kit().key() + ".description")
                        + "\n\n§6" + status)
                .validResultHandler(response -> {
                    if (response.getClickedButtonId() == 0) selectionHandler.accept(player, entry);
                    else if (response.getClickedButtonId() == 1) reopenHandler.accept(player);
                });
        BedrockFormImages.button(form, "§a§l" + (entry.unlocked()
                ? SkyWars.message(player, "skywars.kit.action.select")
                : SkyWars.message(player, "skywars.kit.purchase_select")), imageId(entry));
        BedrockFormImages.button(form, "§f§l" + SkyWars.message(player, "skywars.ui.back"), "actions/back");
        BedrockFormImages.button(form, "§c§l" + SkyWars.message(player, "skywars.ui.close"), "actions/close");
        send(player, form.build());
    }

    static String imageId(KitMenuSnapshot.Entry entry) {
        return "kits/skywars/" + entry.kit().key();
    }

    private void send(Player player, org.geysermc.cumulus.form.Form form) {
        FloodgatePlayer floodgatePlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
        if (floodgatePlayer != null) floodgatePlayer.sendForm(form);
    }
}
