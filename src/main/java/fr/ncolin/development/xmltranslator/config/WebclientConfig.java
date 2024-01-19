package fr.ncolin.development.xmltranslator.config;

import io.micrometer.common.util.StringUtils;
import io.netty.handler.logging.LogLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

@Configuration
@RequiredArgsConstructor
public class WebclientConfig {

	private final WebclientProperties webclientProperties;
	private final DeeplProperties deeplProperties;

	@Bean
	public WebClient webClient() {
		return initializeWebClient(
				this.webclientProperties.getProxyHost(),
				this.webclientProperties.getProxyPort())
				.build();
	}

	private WebClient.Builder initializeWebClient(String proxyHost, Integer proxyPort) {
		HttpClient httpClient = HttpClient.create();

		httpClient
				.wiretap(this.getClass().getCanonicalName(), LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL);

		addProxyToHttpClient(
				httpClient,
				proxyHost,
				proxyPort);

		return WebClient.builder()
				.clientConnector(new ReactorClientHttpConnector(httpClient))
				.defaultHeaders(httpHeaders -> {
					httpHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
					httpHeaders.add(HttpHeaders.AUTHORIZATION, this.deeplProperties.getDeeplAuthKey());
				});
	}

	/**
	 * Si proxyHost et proxyPort sont renseignés, ajoute un proxy au client http
	 *
	 * @param httpClient client http
	 * @param proxyHost  hôte du proxy
	 * @param proxyPort  port du proxy
	 */
	private void addProxyToHttpClient(final HttpClient httpClient, String proxyHost, Integer proxyPort) {
		if (StringUtils.isNotBlank(proxyHost) && proxyPort != null) {
			httpClient.proxy(proxy -> proxy.type(ProxyProvider.Proxy.HTTP).host(proxyHost).port(proxyPort));
		}
	}
}
