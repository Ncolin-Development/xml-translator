package fr.ncolin.development.xmltranslator.service;

import fr.ncolin.development.xmltranslator.XmlTranslatorApplication;
import fr.ncolin.development.xmltranslator.config.DeeplProperties;
import fr.ncolin.development.xmltranslator.model.TranslationRequest;
import fr.ncolin.development.xmltranslator.model.TranslationResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class XmlParser {

	public static final int BATCH_SIZE = 100;
	private static final String FORMAT_XSLT = "src/main/resources/format.xslt";

	private final WebClient webClient;
	private final DeeplProperties deeplProperties;

	@PostConstruct
	public void postConstruct() {
		this.findAllElementToTranslate();
	}

	public List<String> findAllElementToTranslate() {
		List<Path> files = this.getFiles();
		Map<String, List<String>> toTranslateByFile = new HashMap<>();
		files.stream()
				//.peek(System.out::println)
				.filter(path -> path.toString().contains(".fr."))
				.map(path -> {
					try {
						return Tuples.of(path, Files.newInputStream(path));
					} catch (IOException e) {
						return null;
					}
				})
				.filter(Objects::nonNull)
				.forEach(tuple -> {
					toTranslateByFile.put(tuple.getT1().toString(), new ArrayList<>());

					try {
						DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
						DocumentBuilder builder = builderFactory.newDocumentBuilder();
						Document xmlDocument = builder.parse(tuple.getT2());
						String expression = "/root/data";
						XPath xPath = XPathFactory.newInstance().newXPath();
						NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
						for (int i = 0; i < nodeList.getLength(); i++) {
							Node node = nodeList.item(i);
							for (int j = 0; j < node.getChildNodes().getLength(); j++) {
								Node child = node.getChildNodes().item(j);
								if (Node.ELEMENT_NODE == child.getNodeType() && (null == child.getTextContent() || child.getTextContent().isBlank())) {
									String dataName = this.getDataName(node);
									toTranslateByFile.get(tuple.getT1().toString()).add(dataName);
									break;
								}
							}
						}
					} catch (XPathExpressionException | ParserConfigurationException | IOException | SAXException e) {
						System.err.println("error: " + e.getMessage());
					}
				});

		toTranslateByFile
				.forEach((key, value) -> {
					final Map<String, String> toTranslateEn = new HashMap<>();
					final Map<String, String> toTranslateEs = new HashMap<>();
					TranslationRequest requestEn = new TranslationRequest();
					requestEn.setTarget_lang("FR");
					requestEn.setSource_lang("EN");
					requestEn.setText(new ArrayList<>());
					TranslationRequest requestEs = new TranslationRequest();
					requestEs.setTarget_lang("FR");
					requestEs.setSource_lang("ES");
					requestEs.setText(new ArrayList<>());
					try (InputStream is = new FileInputStream(key)) {
						DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
						DocumentBuilder builder = builderFactory.newDocumentBuilder();
						Document translatedDoc = builder.parse(is);
						for (int i = 0; i < value.size(); i++) {
							this.find(key, value.get(i), toTranslateEs, toTranslateEn);

							if (toTranslateEn.size() == BATCH_SIZE) {
								requestEn.setText(toTranslateEn.values().stream().toList());
								List<String> translatedEn = this.getTranslations(requestEn);
								for (int j = 0; j < toTranslateEn.values().stream().toList().size(); j++) {
									this.setInXml(translatedDoc, toTranslateEn.keySet().stream().toList().get(j), translatedEn.get(j));
								}
								toTranslateEn.clear();
							}
							if (toTranslateEs.size() == BATCH_SIZE) {
								requestEs.setText(toTranslateEs.values().stream().toList());
								List<String> translatedEs = this.getTranslations(requestEs);
								for (int j = 0; j < toTranslateEs.values().stream().toList().size(); j++) {
									this.setInXml(translatedDoc, toTranslateEs.keySet().stream().toList().get(j), translatedEs.get(j));
								}
								toTranslateEs.clear();
							}
						}
						requestEn.setText(toTranslateEn.values().stream().toList());
						List<String> translatedEn = this.getTranslations(requestEn);
						for (int j = 0; j < toTranslateEn.values().stream().toList().size(); j++) {
							this.setInXml(translatedDoc, toTranslateEn.keySet().stream().toList().get(j), translatedEn.get(j));
						}

						requestEs.setText(toTranslateEs.values().stream().toList());
						List<String> translatedEs = this.getTranslations(requestEs);
						for (int j = 0; j < toTranslateEs.values().stream().toList().size(); j++) {
							this.setInXml(translatedDoc, toTranslateEs.keySet().stream().toList().get(j), translatedEs.get(j));
						}
						try (FileOutputStream output =
									 new FileOutputStream(key + ".translated")) {
							writeXml(translatedDoc, output);
						}
					} catch (Exception e) {
						System.err.println(e.getMessage());
					}

				});

		return new ArrayList<>();
	}

	private void find(String path, String name, final Map<String, String> es, final Map<String, String> en) {
		try {
			String[] split = path.split("\\.");
			String spanishFileName = split[0] + ".es." + split[2];
			String englishFileName = split[0] + "." + split[2];
			DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = builderFactory.newDocumentBuilder();
			Document spanishDocument = builder.parse(new File(spanishFileName));

			String find = "/root/data[@name='" + name + "']/value/text()";
			XPath spanishXpath = XPathFactory.newInstance().newXPath();
			NodeList spanishNodeList = (NodeList) spanishXpath.compile(find).evaluate(spanishDocument, XPathConstants.NODESET);

			if (0 == spanishNodeList.getLength() || null == spanishNodeList.item(0).getTextContent() || spanishNodeList.item(0).getTextContent().isBlank()) {
				Document englishDocument = builder.parse(new File(englishFileName));
				XPath enXpath = XPathFactory.newInstance().newXPath();
				NodeList enNodeList = (NodeList) enXpath.compile(find).evaluate(englishDocument, XPathConstants.NODESET);
				if (0 == enNodeList.getLength() || null == enNodeList.item(0).getTextContent() || enNodeList.item(0).getTextContent().isBlank()) {
					System.err.println(name + " has no equivalent in english file");
				} else {
					en.put(name, enNodeList.item(0).getTextContent());
				}
			} else {
				es.put(name, spanishNodeList.item(0).getTextContent());
			}
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
	}

	private void setInXml(Document doc, String name, String translated) {

		try {
			String find = "/root/data[@name='" + name + "']/value";
		XPath translationXpath = XPathFactory.newInstance().newXPath();
		NodeList translatedNodes = (NodeList) translationXpath.compile(find).evaluate(doc, XPathConstants.NODESET);
		translatedNodes.item(0).setTextContent(translated);
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}

	}

	// write doc to output stream
	private static void writeXml(Document doc,
								 OutputStream output)
			throws TransformerException, UnsupportedEncodingException {

		TransformerFactory transformerFactory = TransformerFactory.newInstance();

		// The default add many empty new line, not sure why?
		// https://mkyong.com/java/pretty-print-xml-with-java-dom-and-xslt/
		// Transformer transformer = transformerFactory.newTransformer();

		// add a xslt to remove the extra newlines
		Transformer transformer = transformerFactory.newTransformer(
				new StreamSource(new File(FORMAT_XSLT)));

		// pretty print
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.STANDALONE, "no");

		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(output);

		transformer.transform(source, result);

	}

	private List<String> getTranslations(TranslationRequest request) {
		if (0 >= request.getText().size()) {
			return new ArrayList<>();
		}
		ResponseEntity<TranslationResponse> block = this.webClient.post()
				.uri(this.deeplProperties.getDeeplUriBase() + this.deeplProperties.getDeeplUriTranslate())
				.bodyValue(request)
				.retrieve()
				.toEntity(TranslationResponse.class)
				.block();
		if (null == block) {
			return new ArrayList<>();
		}

		TranslationResponse response = block
				.getBody();
		if (null == response) {
			return new ArrayList<>();
		}
		return response.getTranslations().stream()
				.map(TranslationResponse.Translation::getText)
				.filter(Objects::nonNull)
				.toList();
		/*return request.getText().stream()
				.toList();*/
	}

	private String getDataName(Node node) {
		NamedNodeMap attributes = node.getAttributes();
		Node name = attributes.getNamedItem("name");
		return name.getNodeValue();
	}


	private List<Path> getFiles() {
		try {
			File file = new File(XmlTranslatorApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
			String basePath = file.getParent();
			Path dir = Paths.get(basePath);
			try (Stream<Path> fileStream = Files.list(dir)) {
				return fileStream.toList();
			}
		} catch (URISyntaxException | IOException e) {
			return new ArrayList<>();
		}
	}
}
