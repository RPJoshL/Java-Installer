package de.rpjosh.installer;

/**
 * A static class for the main translations
 */
public class Tr {
	
	public final static TranslationService translationService = new TranslationService("translation.de-rpjosh-installer");
	
	/**
	 * {@link de.rpjosh.installer.TranslationService#get(String, String...)}
	 */
	public static String get(String property, String... replaceStrings) {
		return translationService.get(property, replaceStrings);
	}
	
}
