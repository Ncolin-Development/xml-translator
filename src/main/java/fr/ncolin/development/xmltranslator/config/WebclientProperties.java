package fr.ncolin.development.xmltranslator.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class WebclientProperties {

	@Value("${http.proxy.host}")
	private String proxyHost;

	@Value("${http.proxy.port}")
	private Integer proxyPort;
}
