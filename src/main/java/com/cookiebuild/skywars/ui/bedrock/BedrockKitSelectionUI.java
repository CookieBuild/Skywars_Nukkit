package com.cookiebuild.skywars.ui.bedrock;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import com.cookiebuild.skywars.SkyWars;
import com.cookiebuild.skywars.ui.KitMenuSnapshot;
import com.cookiebuild.cookiedough.ui.BedrockButtonText;
import com.cookiebuild.cookiedough.ui.BedrockFormImages;
import com.cookiebuild.cookiedough.ui.BedrockMenuSessionRegistry;
import com.cookiebuild.cookiedough.ui.MainThreadPlayerAction;

/** Native Bedrock selector/shop mirroring the Java inventory workflow. */
public final class BedrockKitSelectionUI {
    private final BiConsumer<Player, KitMenuSnapshot.Entry> selectionHandler;
    private final Consumer<Player> reopenHandler;
    private final BedrockMenuSessionRegistry sessions;

    public BedrockKitSelectionUI(BiConsumer<Player, KitMenuSnapshot.Entry> selectionHandler,
            Consumer<Player> reopenHandler) {
        this(selectionHandler, reopenHandler, new BedrockMenuSessionRegistry());
    }

    BedrockKitSelectionUI(BiConsumer<Player, KitMenuSnapshot.Entry> selectionHandler,
            Consumer<Player> reopenHandler, BedrockMenuSessionRegistry sessions) {
        this.selectionHandler = selectionHandler;
        this.reopenHandler = reopenHandler;
        this.sessions = sessions;
    }

    public void open(Player player, KitMenuSnapshot snapshot) {
        String scope = "skywars:kits";
        UUID nonce = sessions.issue(player.getUniqueId(), scope);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title("§l§6" + SkyWars.message(player, "skywars.kit.catalog"))
                .content("§9§l" + SkyWars.message(player, "skywars.kit.level", snapshot.level())
                        + "  §6§l" + SkyWars.message(player, "skywars.kit.coins", snapshot.coins())
                        + "\n§a§l" + SkyWars.message(player, "skywars.kit.xp", snapshot.experience(),
                                snapshot.nextLevelExperience())
                        + "\n\n" + SkyWars.message(player, "skywars.kit.bedrock.help"));
        List<KitMenuSnapshot.Entry> entries = snapshot.entries();
        for (KitMenuSnapshot.Entry entry : entries) {
            String prefix = entry.selected() ? "★ " : entry.unlocked() ? "✓ " : "🏪 ";
            String detail = SkyWars.message(player, "skywars.kit." + entry.kit().key() + ".description");
            BedrockFormImages.button(builder, BedrockButtonText.format(prefix + entry.kit().displayName(), detail),
                    imageId(entry));
        }
        BedrockFormImages.button(builder, BedrockButtonText.format(SkyWars.message(player, "skywars.ui.close")),
                "actions/close");
        builder.validResultHandler(response -> {
            int index = response.getClickedButtonId();
            MainThreadPlayerAction.dispatch(SkyWars.getInstance(), player, () -> {
                if (!sessions.consume(player.getUniqueId(), nonce, scope)) return;
                if (index >= 0 && index < entries.size()) openDetail(player, entries.get(index));
            });
        });
        builder.closedOrInvalidResultHandler(() -> sessions.invalidate(player.getUniqueId(), nonce, scope));
        if (!send(player, builder.build())) sessions.invalidate(player.getUniqueId(), nonce, scope);
    }

    private void openDetail(Player player, KitMenuSnapshot.Entry entry) {
        String scope = "skywars:kit:" + entry.kit().key();
        UUID nonce = sessions.issue(player.getUniqueId(), scope);
        String status = entry.selected() ? SkyWars.message(player, "skywars.kit.action.selected")
                : entry.unlocked() ? SkyWars.message(player, "skywars.kit.action.select")
                : SkyWars.message(player, "skywars.kit.action.purchase", entry.kit().price());
        SimpleForm.Builder form = SimpleForm.builder()
                .title("§l§6" + entry.kit().displayName())
                .content(SkyWars.message(player, "skywars.kit." + entry.kit().key() + ".description")
                        + "\n\n§6" + status)
                .validResultHandler(response -> {
                    int index = response.getClickedButtonId();
                    MainThreadPlayerAction.dispatch(SkyWars.getInstance(), player, () -> {
                        if (!sessions.consume(player.getUniqueId(), nonce, scope)) return;
                        if (index == 0) selectionHandler.accept(player, entry);
                        else if (index == 1) reopenHandler.accept(player);
                    });
                })
                .closedOrInvalidResultHandler(() ->
                        sessions.invalidate(player.getUniqueId(), nonce, scope));
        BedrockFormImages.button(form, BedrockButtonText.format(entry.unlocked()
                ? SkyWars.message(player, "skywars.kit.action.select")
                : SkyWars.message(player, "skywars.kit.purchase_select")), imageId(entry));
        BedrockFormImages.button(form, BedrockButtonText.format(SkyWars.message(player, "skywars.ui.back")),
                "actions/back");
        BedrockFormImages.button(form, BedrockButtonText.format(SkyWars.message(player, "skywars.ui.close")),
                "actions/close");
        if (!send(player, form.build())) sessions.invalidate(player.getUniqueId(), nonce, scope);
    }

    static String imageId(KitMenuSnapshot.Entry entry) {
        return "kits/skywars/" + entry.kit().key();
    }

    public void invalidate(Player player) {
        sessions.invalidate(player.getUniqueId());
    }

    private boolean send(Player player, org.geysermc.cumulus.form.Form form) {
        FloodgatePlayer floodgatePlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
        if (floodgatePlayer == null) return false;
        floodgatePlayer.sendForm(form);
        return true;
    }
}
