
package au.org.ala.profile

import grails.testing.mixin.integration.Integration
import spock.lang.Specification

@Integration
class DatabaseIsolationSpec extends Specification {

    def "integration tests must connect to profiles-test"() {
        when:
        String databaseName = Opus.collection.getNamespace().getDatabaseName()

        println "Integration test MongoDB database: ${databaseName}"

        then:
        databaseName == "profiles-test"
    }
}
