package com.cookiebuild.skywars.ui.bedrock;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import com.cookiebuild.skywars.SkyWars;
import com.cookiebuild.skywars.ui.KitMenuSnapshot;

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
                .title("§l§6SkyWars Kits")
                .content("§9§lLevel: §f§l" + snapshot.level()
                        + "  §6§lCoins: §f§l" + snapshot.coins()
                        + "\n§a§lXP: §f§l" + snapshot.experience() + "/" + snapshot.nextLevelExperience()
                        + "\n\n§7Choose an owned kit or open its shop page.");
        List<KitMenuSnapshot.Entry> entries = snapshot.entries();
        for (KitMenuSnapshot.Entry entry : entries) {
            String prefix = entry.selected() ? "§6§l★ " : entry.unlocked() ? "§a§l✓ " : "§e§l🏪 ";
            String suffix = entry.unlocked() ? "" : "\n§f" + entry.kit().price() + " coins";
            builder.button(prefix + "§f§l" + entry.kit().displayName() + suffix);
        }
        builder.validResultHandler(response -> {
            int index = response.getClickedButtonId();
            if (index < 0 || index >= entries.size()) return;
            KitMenuSnapshot.Entry entry = entries.get(index);
            if (entry.unlocked()) {
                selectionHandler.accept(player, entry);
            } else {
                openPurchase(player, entry);
            }
        });
        send(player, builder.build());
    }

    private void openPurchase(Player player, KitMenuSnapshot.Entry entry) {
        ModalForm form = ModalForm.builder()
                .title("§l§6" + entry.kit().displayName())
                .content(SkyWars.message(player, "skywars.kit." + entry.kit().key() + ".description")
                        + "\n\n§6Price: §f" + entry.kit().price() + " coins")
                .button1("§a§lPurchase and Select")
                .button2("§f§lBack")
                .validResultHandler(response -> {
                    if (response.getClickedButtonId() == 0) selectionHandler.accept(player, entry);
                    else reopenHandler.accept(player);
                }).build();
        send(player, form);
    }

    private void send(Player player, org.geysermc.cumulus.form.Form form) {
        FloodgatePlayer floodgatePlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
        if (floodgatePlayer != null) floodgatePlayer.sendForm(form);
    }
}
