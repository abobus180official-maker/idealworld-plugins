package dev.fusion;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FusionMod implements ModInitializer {
	public static final String MOD_ID = "fusion-engine";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// КЛЮЧЕВОЕ: где зарегистрированное сообщение mой payload в реестре, иначе
		// registerGlobalReceiver в клиенте падает с "Cannot register handler as no
		// payload type has been registered with name ...". Регистрируем оба направления:
		//  - serverbound (C2S): отправка команд на сервер/плагин
		//  - clientbound (S2C): приём ответа (handshake) от плагина
		PayloadTypeRegistry.playC2S().register(FusionNetwork.PluginChannelPayload.ID,
				FusionNetwork.PluginChannelPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FusionNetwork.PluginChannelPayload.ID,
				FusionNetwork.PluginChannelPayload.CODEC);

		LOGGER.info("[FusionEngine] Client mod initialised. Channel: " + FusionNetwork.CHANNEL);
	}
}