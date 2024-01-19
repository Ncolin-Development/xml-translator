package fr.ncolin.development.xmltranslator.service;

import fr.ncolin.development.xmltranslator.config.DeeplProperties;
import fr.ncolin.development.xmltranslator.config.WebclientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class TranslateService {

	private final WebClient webclient;
	private final DeeplProperties properties;

	public String translate(String key) {
		return null;
	}
}
