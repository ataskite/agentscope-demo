package com.skloda.agentscope.config;

import io.agentscope.extensions.channel.feishu.FeishuAccessTokenProvider;
import io.agentscope.extensions.channel.feishu.FeishuChannelProperties;
import io.agentscope.extensions.channel.feishu.FeishuOutboundClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * S14: Feishu (Lark) Channel configuration.
 * <p>
 * Activated via {@code --spring.profiles.active=feishu}. Builds the Feishu channel
 * beans ({@link FeishuChannelProperties}, {@link FeishuAccessTokenProvider},
 * {@link FeishuOutboundClient}) used by {@link com.skloda.agentscope.controller.FeishuChannelController}
 * to receive IM webhooks and send replies.
 *
 * <h3>Prerequisites</h3>
 * A Feishu custom app with:
 * <ul>
 *   <li>App ID + App Secret (from Feishu developer console)</li>
 *   <li>Event subscription: URL verification + {@code im.message.receive_v1}</li>
 *   <li>Public callback URL (e.g. via ngrok) pointing to {@code POST /channel/feishu/webhook}</li>
 *   <li>Permissions: {@code im:message}, {@code im:message:send_as_bot}</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * See {@code application-feishu.yml}:
 * <pre>
 * agentscope.channel.feishu:
 *   app-id: cli_xxx
 *   app-secret: xxx
 *   verification-token: xxx
 *   encrypt-key: xxx        # optional, disable for dev
 *   callback-path: /channel/feishu/webhook
 *   default-agent-id: chat-basic
 * </pre>
 */
@Configuration
@Profile("feishu")
public class FeishuChannelConfig {

    @Bean
    public FeishuChannelProperties feishuChannelProperties(
            @Value("${agentscope.channel.feishu.app-id:}") String appId,
            @Value("${agentscope.channel.feishu.app-secret:}") String appSecret,
            @Value("${agentscope.channel.feishu.encrypt-key:}") String encryptKey,
            @Value("${agentscope.channel.feishu.verification-token:}") String verificationToken,
            @Value("${agentscope.channel.feishu.callback-path:/channel/feishu/webhook}") String callbackPath,
            @Value("${agentscope.channel.feishu.api-base:https://open.feishu.cn/open-apis}") String apiBase) {
        return new FeishuChannelProperties(appId, appSecret, encryptKey, verificationToken, callbackPath, apiBase);
    }

    @Bean
    public FeishuAccessTokenProvider feishuAccessTokenProvider(FeishuChannelProperties props) {
        return new FeishuAccessTokenProvider(props.appId(), props.appSecret(), props.apiBase());
    }

    @Bean
    public FeishuOutboundClient feishuOutboundClient(FeishuChannelProperties props,
                                                     FeishuAccessTokenProvider tokenProvider) {
        return new FeishuOutboundClient(props.apiBase(), tokenProvider);
    }
}
