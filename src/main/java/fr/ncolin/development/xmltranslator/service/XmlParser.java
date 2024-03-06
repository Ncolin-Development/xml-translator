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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class XmlParser {

	// number of labels to translate in a single deepl call
	public static final int BATCH_SIZE = 100;
	private static final String FORMAT_XSLT = "src/main/resources/format.xslt";
	private static final String KEY = "#KEY#";

	private final WebClient webClient;
	private final DeeplProperties deeplProperties;

	@PostConstruct
	public void postConstruct() {
		this.findAllElementToTranslate();
	}

	public void findAllElementToTranslate() {
		// 1. get all files
		List<Path> files = this.getFiles();

		// 2. For each french file, find all the codes needing a translation. Ex:
		// All_Resources.fr.resx: [Action_CycleCountSchedule_ActivateAutocreate, Action_CycleCountSchedule_CancelIteration, ...]
		Map<String, List<String>> toTranslateByFile = this.extractLabelsToTranslateByFiles(files);

		// Stats & making sure everything is accounted for
		final AtomicLong nbTranslatedEnglish = new AtomicLong(0);
		final AtomicLong nbTranslatedSpanish = new AtomicLong(0);
		final AtomicLong nbDirectlyTransmitted = new AtomicLong(0);
		final AtomicLong total = new AtomicLong(0);

		// 3. Translate file by file
		toTranslateByFile
				.forEach((filename, labels) -> translateForFile(filename, labels, total, nbTranslatedEnglish, nbTranslatedSpanish, nbDirectlyTransmitted));

		System.out.println("Total: " + total);
		System.out.println("EN: " + nbTranslatedEnglish.get());
		System.out.println("ES: " + nbTranslatedSpanish.get());
		System.out.println("KEY: " + nbDirectlyTransmitted.get());
	}

	private void translateForFile(String fileName, List<String> labelsToTranslate, AtomicLong total, AtomicLong nbTranslatedEnglish, AtomicLong nbTranslatedSpanish, AtomicLong nbDirectlyTransmitted) {
		// 1. Separates labels to translate from different languages or not translate at all
		final Map<String, String> toTranslateEn = new HashMap<>();
		final Map<String, String> toTranslateEs = new HashMap<>();
		final Map<String, String> toSetDirectly = new HashMap<>();

		// 2. Init deepl's translation request
		TranslationRequest requestEn = new TranslationRequest();
		requestEn.setTarget_lang("FR");
		requestEn.setSource_lang("EN");
		requestEn.setText(new ArrayList<>());

		TranslationRequest requestEs = new TranslationRequest();
		requestEs.setTarget_lang("FR");
		requestEs.setSource_lang("ES");
		requestEs.setText(new ArrayList<>());

		// 3. Open stream on the French file
		try (InputStream is = new FileInputStream(fileName)) {
			DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = builderFactory.newDocumentBuilder();
			Document translatedDoc = builder.parse(is);

			System.out.println("Total to translate for file " + fileName + ": " + labelsToTranslate.size());
			for (int i = 0; i < labelsToTranslate.size(); i++) {
				total.incrementAndGet();

				// 3a. Find a base to translate in other languages
				this.findInOtherFiles(fileName, labelsToTranslate.get(i), toTranslateEs, toTranslateEn, toSetDirectly);

				if (i % BATCH_SIZE == 0) {
					// prints cursor every BATCH_SIZE
					System.out.println(i);
				}

				// 3b. When toTranslate list gets to BATCH_SIZE elements, translates the elements and sets them in virtual file
				if (toTranslateEn.size() == BATCH_SIZE) {
					System.out.println("translating english batch");
					System.out.println(i);
					translateAndWrite(nbTranslatedEnglish, requestEn, toTranslateEn, translatedDoc);
				}
				if (toTranslateEs.size() == BATCH_SIZE) {
					System.out.println("translating spanish batch");
					System.out.println(i);
					translateAndWrite(nbTranslatedSpanish, requestEs, toTranslateEs, translatedDoc);
				}

				// debatable if this is necessary... BATCH_SIZE is mainly there to limit http call size to deepl
				if (toSetDirectly.size() == BATCH_SIZE) {
					System.out.println("filling direct batch");
					System.out.println(i);
					nbDirectlyTransmitted.addAndGet(BATCH_SIZE);
					toSetDirectly.forEach((mapKey, mapValue) -> this.setInXml(translatedDoc, mapKey, mapValue));
					toSetDirectly.clear();
				}
			}

			// 4. Translated the rest when we're finished with the label finding
			System.out.println("translating last english batch");
			translateAndWrite(nbTranslatedEnglish, requestEn, toTranslateEn, translatedDoc);

			System.out.println("translating spanish batch");
			translateAndWrite(nbTranslatedSpanish, requestEs, toTranslateEs, translatedDoc);

			System.out.println("inserting not translated fields");
			nbDirectlyTransmitted.addAndGet(toSetDirectly.size());
			toSetDirectly.forEach((mapKey, mapValue) -> this.setInXml(translatedDoc, mapKey, mapValue));

			try (FileOutputStream output =
						 new FileOutputStream(fileName + ".translated")) {
				writeXml(translatedDoc, output);
			}
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
	}

	/**
	 * Calls Deepl then write result in file
	 *
	 * @param nbTranslated counter to increment
	 * @param request deepl request
	 * @param toTranslate Map<Name,Value>
	 * @param translatedDoc doc to update
	 */
	private void translateAndWrite(AtomicLong nbTranslated, TranslationRequest request, Map<String, String> toTranslate, Document translatedDoc) {
		nbTranslated.addAndGet(toTranslate.size());
		request.setText(toTranslate.values().stream().toList());
		List<String> translatedEn = this.getTranslations(request);
		for (int j = 0; j < toTranslate.size(); j++) {
			this.setInXml(translatedDoc, toTranslate.keySet().stream().toList().get(j), translatedEn.get(j));
		}
		toTranslate.clear();
	}

	/**
	 * For each file, extracts label list that have null value. Matches them with filename
	 *
	 * @param files target file
	 * @return Map<path_to_file, [name1, name2, ...]>
	 */
	private Map<String, List<String>> extractLabelsToTranslateByFiles(List<Path> files) {
		final Map<String, List<String>> toTranslateByFile = new HashMap<>();
		files.stream()
				.filter(path -> path.toString().contains(".fr."))
				.map(path -> {
					try {
						return Tuples.of(path, Files.newInputStream(path));
					} catch (IOException e) {
						return null;
					}
				})
				.filter(Objects::nonNull)
				.forEach(tuple -> toTranslateByFile.put(tuple.getT1().toString(), extractLabelToTranslateFromFile(tuple)));
		return toTranslateByFile;
	}

	/**
	 * For one file (represented by a tuple<path_to_file, inputstream>), extracts all labels that have a null value
	 *
	 * @param file target file
	 * @return list of labels
	 */
	private List<String> extractLabelToTranslateFromFile(Tuple2<Path, InputStream> file) {
		List<String> labelsToTranslate = new ArrayList<>();
		try {
			DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = builderFactory.newDocumentBuilder();
			Document xmlDocument = builder.parse(file.getT2());
			// finds all node which path matches /root/data
			String expression = "/root/data";
			XPath xPath = XPathFactory.newInstance().newXPath();
			NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
			// loop through all data nodes
			for (int i = 0; i < nodeList.getLength(); i++) {
				Node node = nodeList.item(i);
				// loop through all nodes inside data node  - should be only one element node: value + other nodes that we skip
				for (int j = 0; j < node.getChildNodes().getLength(); j++) {
					Node child = node.getChildNodes().item(j);
					if (Node.ELEMENT_NODE == child.getNodeType() && (null == child.getTextContent() || child.getTextContent().isBlank())) {
						String dataName = this.getDataName(node);
						labelsToTranslate.add(dataName);
						break;
					}
				}
			}
		} catch (XPathExpressionException | ParserConfigurationException | IOException | SAXException e) {
			System.err.println("error: " + e.getMessage());
		}
		return labelsToTranslate;
	}

	/**
	 * Searches other files for a base version of the name we could translate.
	 * if present in spanish:
	 * 		if contains #KEY#:
	 * 			adds to direct list
	 * 		else
	 * 			add to spanish list
	 * 	else if present in english:
	 * 		if contains #KEY#:
	 * 			adds to direct list
	 * 		else
	 * 			add to english list
	 *
	 * @param path filename
	 * @param name name of label
	 * @param es spanishTranslations
	 * @param en englishTranslations
	 * @param toSetDirectly dontTranslate
	 */
	private void findInOtherFiles(String path, String name, final Map<String, String> es, final Map<String, String> en, final Map<String, String> toSetDirectly) {
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

			if (0 != spanishNodeList.getLength() && spanishNodeList.item(0).getTextContent().contains(KEY)) {
				toSetDirectly.put(name, spanishNodeList.item(0).getTextContent());
			} else if (0 == spanishNodeList.getLength()
					|| null == spanishNodeList.item(0).getTextContent()
					|| spanishNodeList.item(0).getTextContent().isBlank()) {
				Document englishDocument = builder.parse(new File(englishFileName));
				XPath enXpath = XPathFactory.newInstance().newXPath();
				NodeList enNodeList = (NodeList) enXpath.compile(find).evaluate(englishDocument, XPathConstants.NODESET);
				if (0 != spanishNodeList.getLength() && enNodeList.item(0).getTextContent().contains(KEY)) {
					toSetDirectly.put(name, enNodeList.item(0).getTextContent());
				} else if (0 == enNodeList.getLength() || null == enNodeList.item(0).getTextContent() || enNodeList.item(0).getTextContent().isBlank()) {
					// warns that a name has no base in spanish nor in english
					System.err.println(name + " has no equivalent in english nor spanish file");
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
	}

	private String getDataName(Node node) {
		NamedNodeMap attributes = node.getAttributes();
		Node name = attributes.getNamedItem("name");
		return name.getNodeValue();
	}


	/**
	 * Quick and dirty AF. Gets the files found in target/
	 * 
	 * @return list of files
	 */
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
