package au.org.ala.profile

import au.org.ala.profile.util.Utils
import groovy.json.JsonSlurper

class BieService {
    def grailsApplication

    List getClassification(String guid) {
        try {
            String resp = new URL("${grailsApplication.config.getProperty('bie.base.url')}/ws/classification/${guid}").text
            JsonSlurper jsonSlurper = new JsonSlurper()
            jsonSlurper.parseText(resp)
        } catch (Exception e) {
            log.error("Failed to retrieve classification for ${guid}", e)
            null
        }
    }

    Map getSpeciesProfile(String guid) {
        try {
            String resp = new URL("${grailsApplication.config.getProperty('bie.base.url')}/ws/species/${guid}").text
            JsonSlurper jsonSlurper = new JsonSlurper()
            jsonSlurper.parseText(resp)
        } catch (Exception e) {
            log.error("Failed to retrieve species profile for ${guid}", e)
            null
        }
    }

    Set<String> searchForPossibleMatches(String name) {
        Set potentialMatches = [] as HashSet
        long start = System.currentTimeMillis()
        try {
            String url = "${grailsApplication.config.getProperty('bie.base.url')}/ws/search.json?q=${Utils.enc(name)}&q.op=AND"
            log.debug("GET request to ${url}")
            String resp = new URL(url).text

            if (resp) {
                Map json = new JsonSlurper().parseText(resp)

                json?.searchResults?.results?.each {
                    String result = it.acceptedConceptName?.trim()
                    if (result && !potentialMatches.find { it.equalsIgnoreCase(result) }) {
                        potentialMatches << result
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to find potential matches for ${name}", e)
        }

        log.debug("Potential name matches from the BIE for ${name} are: ${potentialMatches} (took ${System.currentTimeMillis() - start}ms")

        potentialMatches
    }

    Set<String> getOtherNames(String name) {
        Set otherNames = [] as HashSet

        try {
            String url = "${grailsApplication.config.getProperty('bie.base.url')}/ws/species/${Utils.enc(name)}"
            log.debug("GET request to ${url}")
            String resp = new URL(url).text

            if (resp) {
                Map json = new JsonSlurper().parseText(resp)

                json.synonyms?.each {
                    otherNames << it.nameString?.trim()
                }

                json.commonNames?.each {
                    otherNames << it.nameString.trim()
                }
            }
        } catch (Exception e) {
            log.error("Failed to find other names for ${name}", e)
        }

        otherNames
    }
}
