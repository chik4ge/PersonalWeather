package com.chikage.personalweather;

import com.google.inject.Inject;
import eu.crushedpixel.sponge.packetgate.api.registry.PacketConnection;
import eu.crushedpixel.sponge.packetgate.api.registry.PacketGate;
import net.minecraft.network.play.server.SPacketChangeGameState;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Plugin(
        id = "personal-weather",
        name = "PersonalWeather",
        description = "add /pweather command to set weather per player.",
        version = "1.0",
        authors = {
                "chikage"
        },
        dependencies = {@Dependency(id = "packetgate")}
)
public class PersonalWeather {

    @Inject
    private Logger logger;

    private Map<UUID, Integer> weatherMap; // 0: clear, 1: rain

    private PacketGate packetGate;

    @Listener
    public void onInitializationEvent(GameStartedServerEvent event) {
        weatherMap = new HashMap<>();

        Optional<PacketGate> optionalPacketGate = Sponge.getServiceManager().provide(PacketGate.class);
        if (optionalPacketGate.isPresent()) {
            packetGate = optionalPacketGate.get();
            initializeCommands();

//            TODO ログイン時、サーバーの天気変更時にもMapの内容を優先する
//            packetGate.registerListener(
//                    new PacketListenerAdapter() {
//                        @Override
//                        public void onPacketRead(PacketEvent packetEvent, PacketConnection connection) {
//                        }
//
//                        @Override
//                        public void onPacketWrite(PacketEvent packetEvent, PacketConnection connection) {
//                            if (packetEvent.getPacket() instanceof SPacketChangeGameState) {
//                                SPacketChangeGameState packet = (SPacketChangeGameState) packetEvent.getPacket();
//
//                                int gameState = ObfuscationReflectionHelper.getPrivateValue(
//                                        SPacketChangeGameState.class,
//                                        packet,
//                                        "field_149140_b"
//                                );
//
//                                if (gameState != 7 && gameState != 1 && gameState != 2) return;
//
//                                UUID uuid = connection.getPlayerUUID();
//                                if (uuid == null || !weatherMap.containsKey(uuid)) return;
//
//                                packetEvent.setPacket(new SPacketChangeGameState(7, weatherMap.get(uuid)));
//                            } else if (packetEvent.getPacket() instanceof SPacketLoginSuccess) {
//                                logger.info("SPacketLoginSuccess");
//                                UUID uuid = connection.getPlayerUUID();
//                                if (uuid == null || !weatherMap.containsKey(uuid)) return;
//
//                                setPlayerWeather(connection, weatherMap.get(uuid));
//                            }
//                        }
//                    },
//                    PacketListener.ListenerPriority.DEFAULT,
//                    SPacketChangeGameState.class,
//                    SPacketLoginSuccess.class
//            );
            logger.info("PersonalWeather has successfully initialized.");
        } else {
            logger.error("PacketGate is not found.");
        }
    }

    private void initializeCommands() {
        CommandSpec personalWeatherCommand = CommandSpec.builder()
                .description(Text.of("PersonalWeather command"))
                .permission("personalweather.command")
                .arguments(GenericArguments.choices(Text.of("weather"), new HashMap<String, String>() {{
                    put("clear", "clear");
                    put("rain", "rain");
                    put("reset", "reset");
                }}))
                .executor((src, args) -> {
                    if (!(src instanceof Player)) {
                        src.sendMessage(Text.of("This command can be executed by player only."));
                        return CommandResult.empty();
                    }
                    Player player = (Player) src;
                    weatherMap.putIfAbsent(player.getUniqueId(), 0);
                    Optional<String> optionalWeather = args.getOne(Text.of("weather"));
                    if (!optionalWeather.isPresent()) {
                        sendMessage(player, "天気を入力してください。");
                        return CommandResult.empty();
                    }

                    String weather = optionalWeather.get();
                    int weatherId;
                    switch (weather) {
                        case "clear":
                            weatherId = 0;
                            break;
                        case "rain":
                            weatherId = 1;
                            break;
                        case "reset":
                            weatherMap.remove(player.getUniqueId());
                            String defaultWeather = player.getWorld().getWeather().getId();
                            int defaultWeatherId = defaultWeather.equals("minecraft:clear") ? 0 : 1;

                            Optional<PacketConnection> connection = packetGate.connectionByPlayer(player);
                            if (connection.isPresent()) {
                                setPlayerWeather(connection.get(), defaultWeatherId);
                                sendMessage(player, "天気をリセットしました。");
                                return CommandResult.success();
                            } else {
                                sendMessage(player, "天気をリセットできませんでした。");
                                return CommandResult.empty();
                            }
                        default:
                            sendMessage(player, "有効な天気を入力してください。 (clear, rain)");
                            return CommandResult.empty();
                    }

                    Optional<PacketConnection> connection = packetGate.connectionByPlayer(player);
                    if (connection.isPresent()) {
                        setPlayerWeather(connection.get(), weatherId);
                        sendMessage(player, "天気を " + weather + " に設定しました。");
                        return CommandResult.success();
                    } else {
                        sendMessage(player, "天気を設定できませんでした。");
                        return CommandResult.empty();
                    }
                })
                .build();

        Sponge.getCommandManager().register(this, personalWeatherCommand, "pweather");
    }

    private void setPlayerWeather(PacketConnection connection, int weatherId) {
//          TODO thunderのときは処理が違う
        SPacketChangeGameState packet = new SPacketChangeGameState(7, weatherId);
        connection.sendPacket(packet);

        UUID uuid = connection.getPlayerUUID();
        weatherMap.put(uuid, weatherId);
    }

    private void sendMessage(Player player, String text) {
        player.sendMessage(Text.of(TextColors.GREEN, "[", TextColors.RED, "PersonalWeather", TextColors.GREEN, "] ", TextColors.YELLOW, text));
    }
}
