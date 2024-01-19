package fr.ncolin.development.xmltranslator.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class DeeplProperties {

	@Value("${deepl.auth.key}")
	private String deeplAuthKey;

	@Value("${deepl.uri.base}")
	private String deeplUriBase;

	@Value("${deepl.uri.translate}")
	private String deeplUriTranslate;
}
