package au.org.ala.profile

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import spock.lang.Specification

@Integration
@Rollback
class BaseIntegrationSpec extends Specification {

    def grailsApplication

    /**
     * MongoDB does not support transactions, so any data changed in test will not be rolled back by grails.
     * Therefore, before each test, loop through all domain classes and drop the mongo collection.
     */
    def setup() {
        // Resolve all collections and their actual MongoDB databases
        // before deleting any documents.
        def collections = grailsApplication.domainClasses.collect {
            it.clazz.collection
        }

        def databaseNames = collections.collect {
            it.getNamespace().getDatabaseName()
        }.toSet()

        // Never allow integration tests to clear the development database.
        if (databaseNames != (['profiles-test'] as Set)) {
            throw new IllegalStateException(
                "Refusing to clear MongoDB collections in ${databaseNames}. " +
                    "Integration tests must use profiles-test."
            )
        }

        // Safe to clean up only after verifying the database.
        collections.each {
            it.remove([:])
        }
    }

     public <T> T  save(T entity) {
        entity.save(flush: true, failOnError: true)
    }
}
