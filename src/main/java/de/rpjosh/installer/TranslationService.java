package de.rpjosh.installer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * A class providing translation support of properties
 * 
 */
public class TranslationService {

	private String resourceFile;
	
	public enum Language {
		
		GERMAN (new Locale("de", "DE")),
		ENGLISH(new Locale("en", "US"));
		
		public final Locale locale;
		
		Language(Locale locale) {
			this.locale = locale;
		}
		
	}
	
	private static Logger logger = new Logger();
	private ResourceBundle bundle;
	private ResourceBundle defaultBundle;
	
	/**
	 * Creates a new instance for translating support
	 * 
	 * @param resourceFile		the property file to use for the translations. For example translation.de.rpjosh.installer
	 */
	public TranslationService(String resourceFile) {
		this.resourceFile = resourceFile;
		
		this.defaultBundle = ResourceBundle.getBundle(resourceFile, Locale.ENGLISH);
		
		Locale osLocale = Locale.getDefault();
		List<Locale> supportedLanguages = (List<Locale>) Arrays.asList(
			new Locale[] { Language.GERMAN.locale, Language.ENGLISH.locale }
		);
		if (supportedLanguages.contains(osLocale))  this.bundle = ResourceBundle.getBundle(resourceFile, osLocale);
		else										this.bundle = defaultBundle;
	}
	
	/**
	 * Creates a new instance for translating and forces the use of a specific language for the translations
	 * (defaulting to the language the operation system provides)
	 * 
	 * @param resourceFile	property file to use for the translations. For example translation.de.rpjosh.installer
	 * @param language		language to force
	 */
	public TranslationService(String resourceFile, Language language) {
		this.resourceFile = resourceFile;
		
		this.defaultBundle = ResourceBundle.getBundle(resourceFile, Locale.ENGLISH);
		this.bundle = ResourceBundle.getBundle(resourceFile, language.locale);
	}
	
	
	/**
	 * Force the use of a specific language for the translations (defaulting to 
	 * the language the operation system provides)
	 * 
	 * @param language		language to force
	 */
	public void setLanguage(Language language) {
		this.bundle = ResourceBundle.getBundle(resourceFile, language.locale);
	}
	
	/**
	 * Returns the translated value of the property
	 * 
	 * @param property		 property to translate
	 * @param replaceStrings strings for replacing {0}, {1}, ... inside the property values starting by zero counting one by one 
	 * 
	 * @return 				translated string for the property
	 */
	public String get(String property, String... replaceStrings) {
		try {
			return replaceValues(property, bundle.getString(property), replaceStrings);
		} catch (Exception ex) {
			logger.log("d", "Cannot find property: \"" + property + "\" for language \"" + bundle.getLocale().getLanguage() + "\" in property file \"" + resourceFile + "\"", "Translations#get");
		}
		
		try {
			return replaceValues(property, defaultBundle.getString(property), replaceStrings);
		} catch (Exception ex) {
			logger.log("e", "Cannot find property: \"" + property + "\" in default translation list from property file \"" + resourceFile + "\"", "Translations#get");
			return property;
		}
	}
	
	private String replaceValues(String property, String value, String... replaceStrings) {
		if (replaceStrings.length == 0) return value;
		
		for (int i = 0; i < replaceStrings.length; i++) {
			if (value.contains("{" + i + "}")) {
				value = value.replace("{" + i + "}", replaceStrings[i]);
			} else {
				logger.log("d", "No value matches for " + "{" + i + "}" + "in the property \"" + property + "\n: " + value, "Translations#replaceValue");
			}
		}
		return value;
	}
	
}
