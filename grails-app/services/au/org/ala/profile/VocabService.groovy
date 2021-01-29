package au.org.ala.profile

import com.mongodb.BasicDBObject
import com.mongodb.WriteResult
import com.mongodb.MongoClient
import com.mongodb.client.MongoCollection
import grails.gorm.transactions.Transactional

import javax.persistence.PersistenceException

@Transactional
class VocabService extends BaseDataAccessService {

    MongoClient mongo
    def grailsApplication

    boolean updateVocab(String vocabId, Map data) {
        log.debug("Updating vocabulary ${vocabId} with data ${data}")
        Vocab vocab = Vocab.findByUuid(vocabId);

        vocab.strict = data.strict as boolean

        if (data.deleteExisting) {
            vocab.terms.clear()
        }

        Set<String> retainedTermIds = []

        data.terms.each { item ->
            if (item.termId) {
                Term term = Term.findByUuid(item.termId)

                if (term) {
                    retainedTermIds << item.termId
                    term.name = item.name
                    term.order = item.order
                    term.required = item.required == null ? false : item.required.toBoolean()
                    term.summary = item.summary == null ? false : item.summary.toBoolean()
                    term.containsName = item.containsName == null ? false : item.containsName.toBoolean()
                }
            } else {
                // GRAILS-8061 beforeValidate does not get called on child records during a cascade save of the parent
                // Therefore, we cannot rely on the beforeValidate method of Term, which usually creates the UUID.
                Term term = new Term(uuid: UUID.randomUUID().toString(),
                        name: item.name,
                        order: item.order ?: vocab.terms.size() + 1,
                        required: item.required.toBoolean(),
                        summary: item.summary.toBoolean(),
                        containsName: item.containsName.toBoolean())
                term.vocab = vocab
                vocab.terms << term
            }
        }

        vocab.terms.each { item ->
            if (!retainedTermIds.contains(item.uuid)) {
                Term term = Term.findByUuid(item.uuid);
                if (term) {
                    term.vocab = null
                    log.debug("Deleting term ${item.name}")
                    term.delete()
                }
            }
        }

        save vocab
    }

    Term getOrCreateTerm(String name, String vocabId, String excludeTerm = null) {
        Term term

        Vocab vocab = Vocab.findByUuid(vocabId);

        if (vocab) {
            term = excludeTerm? Term.findByVocabAndUuidNotEqualAndNameIlike(vocab, excludeTerm, name) : Term.findByVocabAndNameIlike(vocab, name)

            if (!term) {
                if (vocab.strict) {
                    throw new IllegalStateException("Term ${name} does not exist in the vocabulary, and cannot be created because the vocabulary is Strict.")
                } else {
                    term = new Term(name: name, vocab: vocab)
                    boolean success = save term
                    if (!success) {
                        throw new PersistenceException("Failed to insert new term.")
                    }
                }
            }
        } else {
            throw new PersistenceException("Vocabulary with id ${vocabId} does not exist")
        }

        term
    }

    int findUsagesOfTerm(String opusId, String vocabId, String termUuid) {

        Term term = Term.findByVocabAndUuid(Vocab.findByUuid(vocabId), termUuid)
        Opus opus = Opus.findByUuid(opusId)

        // Check if this is acknowledgement term
        if (opus.authorshipVocabUuid == vocabId) {
            List<Profile> profiles = Profile.findAll {authorship.category == term.id || draft.authorship.category == term.id}

            return profiles.size()

        } else {
            List<Attribute> attributes = Attribute.findAllByTitle(term)

            attributes.size()

            // Draft for profiles store attributes as well
            List<Profile> profiles = Profile.findAll {draft.attributes.title == term.id}

            return (attributes.size() + profiles.size())

        }
    }

    Map<String, Integer> replaceUsagesOfTerm(String opusId, jsonMap) {

        def json = jsonMap.list
        log.debug("Replacing vocabulary term usages: ${json}")

        Map<String, Integer> replacedUsages = [:]

        Opus opus = Opus.findByUuid(opusId)

        json.each { replacement ->
             int replaced = replaceTerm(opus, replacement.vocabId, replacement.existingTermId, replacement.newTermName)

            replacedUsages << [(replacement.existingTermName): replaced]
        }

        replacedUsages
    }



    def replaceTerm = { opus, vocabId, existingTermId, newTermName ->

        def db = mongo.getDatabase(grailsApplication.config.grails.mongodb.databaseName)
        int replacedUsages = 0

        // There can be more than 1 terms having same term names. So, checking by term id is necessary.
        Term existingTerm = Term.findByVocabAndUuid(Vocab.findByUuid(vocabId), existingTermId)
        Term newTerm = getOrCreateTerm(newTermName, vocabId, existingTermId)

        //Make sure both existing and new terms are valid before updating
        if (existingTerm && newTerm) {

            // Check if this is acknowledgement term
            if (opus.authorshipVocabUuid == vocabId) {

                // Bulk update for profiles and profiles draft acknowledgement term as GORM update takes long time for many records.
                MongoCollection<BasicDBObject> profileCollection = db.getCollection("profile", BasicDBObject.class)
                def updateQuery = new BasicDBObject('$set', new BasicDBObject('authorship.$.category', newTerm.id))
                def searchQuery = new BasicDBObject(['authorship.category': existingTerm.id])
                WriteResult updateResult = profileCollection.updateMulti(searchQuery, updateQuery)
                if (updateResult && updateResult.n) {
                    replacedUsages = updateResult.n
                }

                def draftUpdateQuery = new BasicDBObject('$set', new BasicDBObject('draft.authorship.$.category', newTerm.id))
                def draftSearchQuery = new BasicDBObject(['draft.authorship.category': existingTerm.id])
                WriteResult draftUpdateResult = profileCollection.updateMulti(draftSearchQuery, draftUpdateQuery)
                if (draftUpdateResult && draftUpdateResult.n) {
                    replacedUsages += draftUpdateResult.n
                }

            } else {

                MongoCollection<BasicDBObject> profileCollection = db.getCollection("profile", BasicDBObject.class)
                MongoCollection<BasicDBObject> attributeCollection = db.getCollection("attribute", BasicDBObject.class)

                def attributeUpdateQuery = new BasicDBObject('$set', new BasicDBObject('title', newTerm.id))
                def attributeSearchQuery = new BasicDBObject('title': existingTerm.id)
                WriteResult attributeUpdateResult = attributeCollection.updateMulti(attributeSearchQuery, attributeUpdateQuery)
                if (attributeUpdateResult && attributeUpdateResult.n) {
                    replacedUsages = attributeUpdateResult.n
                }

                def draftUpdateQuery = new BasicDBObject('$set', new BasicDBObject('draft.attributes.$.title', newTerm.id))
                def draftSearchQuery = new BasicDBObject(['draft.attributes.title': existingTerm.id])
                WriteResult draftUpdateResult = profileCollection.updateMulti(draftSearchQuery, draftUpdateQuery)
                if (draftUpdateResult && draftUpdateResult.n) {
                    replacedUsages += draftUpdateResult.n
                }

            }

            if (replacedUsages > 0) {
                existingTerm.delete()
            }
        }

        replacedUsages
    }
}
