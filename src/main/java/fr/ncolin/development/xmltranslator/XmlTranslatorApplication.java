package fr.ncolin.development.xmltranslator;

import fr.ncolin.development.xmltranslator.model.ChosenFiles;
import fr.ncolin.development.xmltranslator.service.XmlParser;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.miginfocom.swing.MigLayout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;

@SpringBootApplication
public class XmlTranslatorApplication {


	public static void main(String[] args) {

		var ctx = new SpringApplicationBuilder(XmlTranslatorApplication.class)
				.headless(false).web(WebApplicationType.NONE).run(args);
	}
}
