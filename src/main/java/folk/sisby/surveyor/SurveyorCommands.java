package folk.sisby.surveyor;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.LandmarkType;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.util.TextUtil;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class SurveyorCommands {
    private static final Multimap<UUID, UUID> requests = HashMultimap.create();

    private static void informGroup(ServerPlayerEntity player, Set<PlayerSummary> group, Consumer<Text> feedback) {
        feedback.accept(
            Text.literal("You're sharing your map with ").formatted(Formatting.GOLD)
                .append(Text.literal("%d".formatted(group.size() - 1)).formatted(Formatting.WHITE))
                .append(Text.literal(" other" + (group.size() - 1 > 1 ? " players:" : " player:")).formatted(Formatting.GOLD))
        );
        feedback.accept(
            TextUtil.highlightStrings(group.stream().map(PlayerSummary::username).filter(u -> !u.equals(player.getGameProfile().getName())).toList(), s -> Formatting.WHITE).formatted(Formatting.GOLD)
        );
    }

    private static int informGlobal(ServerSummary serverSummary, ServerPlayerEntity player, SurveyorExploration exploration, String ignored, Consumer<Text> feedback) {
        feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("The server has global sharing enabled!").formatted(Formatting.YELLOW)));
        feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You can't leave or modify the global sharing group!").formatted(Formatting.YELLOW)));
        informGroup(player, serverSummary.groupPlayers(player.getUuid(), player.getServer()), feedback);
        return 0;
    }

    private static int info(ServerSummary serverSummary, ServerPlayerEntity player, SurveyorExploration exploration, String ignored, Consumer<Text> feedback) {
        Set<PlayerSummary> group = serverSummary.groupPlayers(player.getUuid(), player.getServer());
        SurveyorExploration groupExploration = SurveyorExploration.ofShared(player);
        Set<Landmark<?>> landmarks = new HashSet<>();
        Set<Landmark<?>> waypoints = new HashSet<>();
        Set<Landmark<?>> groupLandmarks = new HashSet<>();
        Set<Landmark<?>> groupWaypoints = new HashSet<>();
        for (ServerWorld world : player.getServer().getWorlds()) {
            WorldLandmarks summary = WorldSummary.of(world).landmarks();
            if (summary != null) {
                summary.asMap(exploration).forEach((type, inner) -> inner.forEach((pos, landmark) -> (landmark.owner() == null ? landmarks : waypoints).add(landmark)));
                summary.asMap(groupExploration).forEach((type, inner) -> inner.forEach((pos, landmark) -> (landmark.owner() == null ? groupLandmarks : groupWaypoints).add(landmark)));
            }
        }
        feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("---Map Exploration Summary---").formatted(Formatting.GRAY)));
        feedback.accept(
            Text.literal("You've explored ").formatted(Formatting.AQUA)
                .append(Text.literal("%d".formatted(exploration.chunkCount())).formatted(Formatting.WHITE))
                .append(Text.literal(" total chunks!").formatted(Formatting.AQUA))
                .append(
                    group.size() <= 1 ? Text.empty() :
                        Text.literal(" (").formatted(Formatting.AQUA)
                            .append(Text.literal("%d".formatted(groupExploration.chunkCount())).formatted(Formatting.WHITE))
                            .append(Text.literal(" with friends)").formatted(Formatting.AQUA))
                )
        );
        feedback.accept(
            Text.literal("You've explored ").formatted(Formatting.LIGHT_PURPLE)
                .append(Text.literal("%d".formatted(exploration.structureCount())).formatted(Formatting.WHITE))
                .append(Text.literal(" structures!").formatted(Formatting.LIGHT_PURPLE))
                .append(
                    group.size() <= 1 ? Text.empty() :
                        Text.literal(" (").formatted(Formatting.LIGHT_PURPLE)
                            .append(Text.literal("%d".formatted(groupExploration.structureCount())).formatted(Formatting.WHITE))
                            .append(Text.literal(" with friends)").formatted(Formatting.LIGHT_PURPLE))
                )
        );
        feedback.accept(
            Text.literal("You've explored ").formatted(Formatting.GREEN)
                .append(Text.literal("%d".formatted(landmarks.size())).formatted(Formatting.WHITE))
                .append(Text.literal(" landmarks!").formatted(Formatting.GREEN))
                .append(
                    group.size() <= 1 ? Text.empty() :
                        Text.literal(" (").formatted(Formatting.GREEN)
                            .append(Text.literal("%d".formatted(groupLandmarks.size())).formatted(Formatting.WHITE))
                            .append(Text.literal(" with friends)").formatted(Formatting.GREEN))
                )
        );
        feedback.accept(
            Text.literal("...and created ").formatted(Formatting.GREEN)
                .append(Text.literal("%d".formatted(waypoints.size())).formatted(Formatting.WHITE))
                .append(Text.literal(" waypoints!").formatted(Formatting.GREEN))
                .append(
                    group.size() <= 1 ? Text.empty() :
                        Text.literal(" (").formatted(Formatting.GREEN)
                            .append(Text.literal("%d".formatted(groupWaypoints.size())).formatted(Formatting.WHITE))
                            .append(Text.literal(" with friends)").formatted(Formatting.GREEN))
                )
        );
        if (group.size() > 1) {
            informGroup(player, group, feedback);
        }
        feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("-------End Summary-------").formatted(Formatting.GRAY)));
        return 1;
    }

    private static int landmarkInfo(ServerSummary serverSummary, ServerPlayerEntity player, SurveyorExploration exploration, String ignored, Consumer<Text> feedback) {
        Set<PlayerSummary> group = serverSummary.groupPlayers(player.getUuid(), player.getServer());
        SurveyorExploration groupExploration = SurveyorExploration.ofShared(player);
        feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("---Landmark Types---").formatted(Formatting.GRAY)));
        Set<LandmarkType<?>> waypoints = new HashSet<>();
        Multimap<LandmarkType<?>, BlockPos> keys = HashMultimap.create();
        Multimap<LandmarkType<?>, BlockPos> groupKeys = HashMultimap.create();
        Multimap<LandmarkType<?>, BlockPos> personalKeys = HashMultimap.create();
        for (ServerWorld world : player.getServer().getWorlds()) {
            WorldLandmarks summary = WorldSummary.of(world).landmarks();
            if (summary != null) {
                waypoints.addAll(summary.asMap(null).values().stream().flatMap(e -> e.values().stream().filter(l -> l.owner() != null)).map(Landmark::type).toList());
                keys.putAll(summary.keySet(null));
                groupKeys.putAll(summary.keySet(groupExploration));
                personalKeys.putAll(summary.keySet(exploration));
            }
        }
        keys.asMap().forEach((type, list) -> {
            feedback.accept(
                Text.literal("%s".formatted(type.id())).formatted(Formatting.WHITE)
                    .append(Text.literal(waypoints.contains(type) ? ": created " : ": explored ").formatted(Formatting.AQUA))
                    .append(Text.literal("%d".formatted(personalKeys.get(type).size())).formatted(Formatting.WHITE))
                    .append(
                        group.size() <= 1 ? Text.empty() :
                            Text.literal(" (").formatted(Formatting.LIGHT_PURPLE)
                                .append(Text.literal("%d".formatted(groupKeys.get(type).size())).formatted(Formatting.WHITE))
                                .append(Text.literal(" shared)").formatted(Formatting.LIGHT_PURPLE))
                    )
                    .append(
                        !player.hasPermissionLevel(2) ? Text.empty() :
                            Text.literal(" {of ").formatted(Formatting.GOLD)
                                .append(Text.literal("%d".formatted(keys.get(type).size())).formatted(Formatting.WHITE))
                                .append(Text.literal("}").formatted(Formatting.GOLD))
                    )
            );
        });
        feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("-------End Landmarks-------").formatted(Formatting.GRAY)));
        return 1;
    }

    private static int share(ServerSummary serverSummary, ServerPlayerEntity player, SurveyorExploration exploration, String username, Consumer<Text> feedback) {
        ServerPlayerEntity sharePlayer = player.getServer().getPlayerManager().getPlayer(username);
        if (sharePlayer == null) {
            feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("Can't find an online player named ").formatted(Formatting.YELLOW)).append(Text.literal(username).formatted(Formatting.WHITE)).append(Text.literal(".").formatted(Formatting.YELLOW)));
            return 0;
        }
        if (sharePlayer == player) {
            feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You can't share map exploration with yourself!").formatted(Formatting.YELLOW)));
            return 0;
        }
        if (requests.containsEntry(player.getUuid(), sharePlayer.getUuid())) { // Accept Request
            if (serverSummary.groupSize(player.getUuid()) > 1 && serverSummary.groupSize(sharePlayer.getUuid()) > 1) {
                feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You're in a group! leave your group first with:").formatted(Formatting.YELLOW)));
                feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("/surveyor unshare").formatted(Formatting.GOLD)));
                return 0;
            }
            requests.removeAll(player.getUuid()); // clear all other requests
            ServerSummary.of(player.getServer()).joinGroup(player.getUuid(), sharePlayer.getUuid(), player.getServer());
            feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You're now sharing map exploration with ").formatted(Formatting.GREEN)).append(Text.literal("%d".formatted(serverSummary.groupSize(player.getUuid()) - 1)).formatted(Formatting.WHITE)).append(Text.literal((serverSummary.groupSize(player.getUuid()) - 1) > 1 ? " players:" : " player:").formatted(Formatting.GREEN)));
            feedback.accept(TextUtil.highlightStrings(serverSummary.groupPlayers(player.getUuid(), player.getServer()).stream().map(PlayerSummary::username).filter(u -> !u.equals(player.getGameProfile().getName())).toList(), s -> Formatting.WHITE).formatted(Formatting.GREEN));
            for (ServerPlayerEntity friend : serverSummary.groupOtherServerPlayers(player.getUuid(), player.getServer())) {
                friend.sendMessage(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(player.getDisplayName().copy().formatted(Formatting.WHITE)).append(Text.literal(" is now sharing their map with you.").formatted(Formatting.AQUA)));
                friend.sendMessage(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You're now sharing map exploration with ").formatted(Formatting.AQUA)).append(Text.literal("%d".formatted(serverSummary.groupSize(player.getUuid()) - 1)).formatted(Formatting.WHITE)).append(Text.literal((serverSummary.groupSize(player.getUuid()) - 1) > 1 ? " players:" : " player:").formatted(Formatting.AQUA)));
                friend.sendMessage(TextUtil.highlightStrings(serverSummary.groupPlayers(player.getUuid(), player.getServer()).stream().map(PlayerSummary::username).filter(u -> !u.equals(friend.getGameProfile().getName())).toList(), s -> Formatting.WHITE).formatted(Formatting.AQUA));
            }
            return 1;
        } else if (!requests.containsEntry(sharePlayer.getUuid(), player.getUuid())) { // Make Request
            requests.put(sharePlayer.getUuid(), player.getUuid());
            feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("Share request sent to ").formatted(Formatting.GREEN)).append(sharePlayer.getDisplayName().copy().formatted(Formatting.WHITE)).append(Text.literal(".").formatted(Formatting.GREEN)));
            sharePlayer.sendMessage(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(player.getDisplayName().copy().formatted(Formatting.WHITE)).append(Text.literal(" wants to share map exploration!").formatted(Formatting.AQUA)));
            if (serverSummary.groupSize(player.getUuid()) <= 1 && serverSummary.groupSize(sharePlayer.getUuid()) <= 1) { // Creating a group
                feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("If accepted, you'll share your map exploration.").formatted(Formatting.GREEN)));
                sharePlayer.sendMessage(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("To share your explored map area, enter:").formatted(Formatting.AQUA)));
            } else if (serverSummary.groupSize(player.getUuid()) <= 1) { // Joining their group
                feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("If accepted, you'll share with their group of ").formatted(Formatting.GREEN)).append(Text.literal("%d".formatted(serverSummary.groupSize(sharePlayer.getUuid()))).formatted(Formatting.WHITE)).append(Text.literal(".").formatted(Formatting.GREEN)));
                sharePlayer.sendMessage(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("To share your group of ").append(Text.literal("%d".formatted(serverSummary.groupSize(sharePlayer.getUuid()))).formatted(Formatting.WHITE)).formatted(Formatting.AQUA)).append(Text.literal(", enter:").formatted(Formatting.AQUA)));
            } else { // Sharing your group
                feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("If accepted, they'll share with your group of ").formatted(Formatting.GREEN)).append(Text.literal("%d".formatted(serverSummary.groupSize(player.getUuid()))).formatted(Formatting.WHITE)).append(Text.literal(".").formatted(Formatting.GREEN)));
                sharePlayer.sendMessage(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("To share with their group of ").append(Text.literal("%d".formatted(serverSummary.groupSize(player.getUuid()))).formatted(Formatting.WHITE)).formatted(Formatting.AQUA)).append(Text.literal(", enter:").formatted(Formatting.AQUA)));
            }
            sharePlayer.sendMessage(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("/surveyor share %s".formatted(player.getGameProfile().getName())).formatted(Formatting.GOLD)));
            return 1;
        } else {
            feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You've already sent this player a share request!").formatted(Formatting.YELLOW)));
            return 0;
        }
    }

    private static int unshare(ServerSummary serverSummary, ServerPlayerEntity player, SurveyorExploration exploration, String ignored, Consumer<Text> feedback) {
        int shareNumber = serverSummary.groupSize(player.getUuid()) - 1;
        if (shareNumber == 0) {
            feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You're not sharing map exploration with anyone!").formatted(Formatting.YELLOW)));
            return 0;
        } else {
            Set<ServerPlayerEntity> friends = serverSummary.groupOtherServerPlayers(player.getUuid(), player.getServer());
            ServerSummary.of(player.getServer()).leaveGroup(player.getUuid(), player.getServer());
            feedback.accept(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("Stopped sharing map exploration with ").formatted(Formatting.GREEN)).append(Text.literal("%d".formatted(shareNumber)).formatted(Formatting.WHITE)).append(Text.literal(shareNumber > 1 ? " players." : " player.").formatted(Formatting.GREEN)));
            for (ServerPlayerEntity friend : friends) {
                int groupSize = serverSummary.groupSize(friend.getUuid()) - 1;
                friend.sendMessage(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(player.getDisplayName().copy().formatted(Formatting.WHITE)).append(Text.literal(" is no longer sharing with you.").formatted(Formatting.AQUA)));
                friend.sendMessage(Text.literal("[Surveyor] ").formatted(Formatting.DARK_RED).append(Text.literal("You're now sharing map exploration with ").formatted(Formatting.AQUA)).append(Text.literal("%d".formatted(groupSize)).formatted(Formatting.WHITE)).append(Text.literal(groupSize == 0 ? " players." : groupSize > 1 ? " players:" : " player:").formatted(Formatting.AQUA)));
                if (groupSize > 0) friend.sendMessage(TextUtil.highlightStrings(serverSummary.groupPlayers(friend.getUuid(), friend.getServer()).stream().map(PlayerSummary::username).filter(u -> !u.equals(friend.getGameProfile().getName())).toList(), s -> Formatting.WHITE).formatted(Formatting.AQUA));
            }
            return 1;
        }
    }

    public interface SurveyorCommandExecutor {
        int execute(ServerSummary serverSummary, ServerPlayerEntity player, SurveyorExploration exploration, String arg, Consumer<Text> feedback);
    }

    public static int execute(CommandContext<ServerCommandSource> context, String arg, SurveyorCommandExecutor executor) {
        ServerPlayerEntity player;
        try {
            player = context.getSource().getPlayerOrThrow();
        } catch (CommandSyntaxException e) {
            Surveyor.LOGGER.error("[Surveyor] Commands cannot be invoked by a non-player");
            return 0;
        }

        SurveyorExploration exploration = SurveyorExploration.of(player);
        try {
            return executor.execute(ServerSummary.of(player.getServer()), player, exploration, arg != null ? context.getArgument(arg, String.class) : null, t -> context.getSource().sendFeedback(() -> t, false));
        } catch (Exception e) {
            context.getSource().sendFeedback(() -> Text.literal("Command failed! Check log for details.").formatted(Formatting.RED), false);
            Surveyor.LOGGER.error("[Surveyor] Error while executing command: {}", context.getInput(), e);
            return 0;
        }
    }

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            CommandManager.literal("surveyor")
                .then(
                    CommandManager.literal("share").then(
                        CommandManager.argument("player", StringArgumentType.word()).suggests((c, b) -> {
                                c.getSource().getServer().getPlayerManager().getPlayerList().stream().filter(p -> c.getSource().getPlayer() != p).map(p -> p.getGameProfile().getName()).forEach(b::suggest);
                                return b.buildFuture();
                            }).requires(c -> !Surveyor.CONFIG.sync.forceGlobal)
                            .executes(c -> execute(c, "player", SurveyorCommands::share))
                    ).requires(c -> Surveyor.CONFIG.sync.forceGlobal)
                        .executes(c -> execute(c, null, SurveyorCommands::informGlobal))
                )
                .then(
                    CommandManager.literal("unshare")
                        .executes(c -> execute(c, null, Surveyor.CONFIG.sync.forceGlobal ? SurveyorCommands::informGlobal : SurveyorCommands::unshare))
                )
                .then(
                    CommandManager.literal("landmarks")
                        .executes(c -> execute(c, null, SurveyorCommands::landmarkInfo))
                )
                .executes(c -> execute(c, null, SurveyorCommands::info))
        );
    }
}
