package fr.ncolin.development.xmltranslator;

import fr.ncolin.development.xmltranslator.model.ChosenFiles;
import fr.ncolin.development.xmltranslator.service.XmlParser;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.miginfocom.swing.MigLayout;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;

@SpringBootApplication
public class XmlTranslatorApplication extends JFrame {

	@Getter
	private JTextField txtFileChosen;
	private JButton btnBrowse;
	private ChosenFiles chosenFiles;
	private JButton btnStart;

	public XmlTranslatorApplication() {
		this.chosenFiles = new ChosenFiles();
		initUI();
	}

	private void initUI() {
		createLayout();

		setTitle("Translation");
		setSize(1200, 200);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
	}

	private void createLayout() {

		var pane = getContentPane();
		MigLayout layout = new MigLayout("wrap 4, filly", "[][][fill, grow][]", "[top][bottom]");
		pane.setLayout(layout);

		pane.add(new JLabel("Files to translate"));

		btnBrowse = new JButton("Browse");
		pane.add(btnBrowse);

		txtFileChosen = new JFormattedTextField("");
		chosenFiles.addPropertyChangeListener(evt -> {
			if ("chosenFiles".equals(evt.getPropertyName())) {
				String newValue = (String) evt.getNewValue();
				txtFileChosen.setText(newValue);
			}
		});
		pane.add(txtFileChosen);

		btnBrowse.addActionListener(actionEvent -> {
			JFileChooser fileChooser = new JFileChooser();
			fileChooser.setMultiSelectionEnabled(true);
			int option = fileChooser.showOpenDialog(XmlTranslatorApplication.this);
			if (option == JFileChooser.APPROVE_OPTION) {
				File[] selectedFiles = fileChooser.getSelectedFiles();
				StringBuilder fileNames = new StringBuilder();
				for (File file : selectedFiles) {
					System.out.printf("file found: %s%n", file);
					if (fileNames.length() > 0) {
						fileNames.append("\n");
					}
					fileNames.append(file.getName());
				}
				this.chosenFiles.setChosenFiles(fileNames.toString());
				pane.validate(); // don't anybody i did that
			}
		});

		this.btnStart = new JButton("Start");
		pane.add(btnStart, "right");

		var quitButton = new JButton("Quit");
		quitButton.setBackground(Color.red);
		quitButton.setForeground(Color.white);
		quitButton.addActionListener((ActionEvent event) -> {
			System.exit(0);
		});
		pane.add(quitButton);
	}

	public static void main(String[] args) {

		var ctx = new SpringApplicationBuilder(XmlTranslatorApplication.class)
				.headless(false).web(WebApplicationType.NONE).run(args);

		EventQueue.invokeLater(() -> {

			var ex = ctx.getBean(XmlTranslatorApplication.class);
			ex.setVisible(true);
		});
	}
}
