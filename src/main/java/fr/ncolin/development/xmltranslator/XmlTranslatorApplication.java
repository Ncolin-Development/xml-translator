package fr.ncolin.development.xmltranslator;

import fr.ncolin.development.xmltranslator.service.XmlParser;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@RequiredArgsConstructor
public class XmlTranslatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(XmlTranslatorApplication.class, args);
	}

}
