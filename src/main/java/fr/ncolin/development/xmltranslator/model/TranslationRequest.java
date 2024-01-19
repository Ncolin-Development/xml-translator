package fr.ncolin.development.xmltranslator.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class TranslationRequest {

	private List<String> text = new ArrayList<>();
	private String source_lang;
	private String target_lang;
}
