package au.org.ala.profile

class ReportServiceSpec extends BaseIntegrationSpec {

    ReportService service = new ReportService()

    def "setup"() {
    }

    def "a recent comments report should show comments in date range but not those that aren't"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")

        Profile profile1 = save new Profile(scientificName: "name1", fullName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        Contributor contributor = save new Contributor(name: 'Agent Smith')

        Comment comment1 = save new Comment(text: "hello1", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment2 = save new Comment(text: "hello2", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment3 = save new Comment(text: "hello3", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment4 = save new Comment(text: "hello4", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment5 = save new Comment(text: "hello5", profileUuid: profile1.uuid, author: contributor, parent: null)

        when:
        Map result = service.recentComments(opus1, comment2.dateCreated, comment4.dateCreated, -1, 0, false)

        then:
        result.recordCount == 3
        result.records.size() == 3
        result.records*.comment.containsAll([comment2, comment3, comment4]*.text) == true
    }

    def "a recent comments report should exclude comments from other Opuses"() {
        given:

        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")

        Profile profile1 = save new Profile(scientificName: "name1", fullName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile2 = save new Profile(scientificName: "name2", fullName: "name2", opus: opus2, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        Contributor contributor = save new Contributor(name: 'Agent Smith')

        Comment comment1 = save new Comment(text: "hello1", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment2 = save new Comment(text: "hello2", profileUuid: profile2.uuid, author: contributor, parent: null)
        Comment comment3 = save new Comment(text: "hello3", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment4 = save new Comment(text: "hello4", profileUuid: profile2.uuid, author: contributor, parent: null)
        Comment comment5 = save new Comment(text: "hello5", profileUuid: profile1.uuid, author: contributor, parent: null)

        when:
        Map result = service.recentComments(opus1, comment1.dateCreated, comment5.dateCreated, -1, 0, false)

        then:
        result.recordCount == 3
        result.records.size() == 3
        result.records*.comment.containsAll([comment1, comment3, comment5]*.text) == true
    }

    def "a count only recent comments report should only return the count"() {
        given:

        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")

        Profile profile1 = save new Profile(scientificName: "name1", fullName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        Contributor contributor = save new Contributor(name: 'Agent Smith')

        Comment comment1 = save new Comment(text: "hello1", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment2 = save new Comment(text: "hello2", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment3 = save new Comment(text: "hello3", profileUuid: profile1.uuid, author: contributor, parent: null)

        when:
        Map result = service.recentComments(opus1, comment1.dateCreated, comment3.dateCreated, -1, 0, true)

        then:
        result.recordCount == 3
        result.records == null
    }

    def "a paged recent comments report should report the correct total but only the page size in results"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")

        Profile profile1 = save new Profile(scientificName: "name1", fullName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        Contributor contributor = save new Contributor(name: 'Agent Smith')

        Comment comment1 = save new Comment(text: "hello1", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment2 = save new Comment(text: "hello2", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment3 = save new Comment(text: "hello3", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment4 = save new Comment(text: "hello4", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment5 = save new Comment(text: "hello5", profileUuid: profile1.uuid, author: contributor, parent: null)

        when:
        Map result = service.recentComments(opus1, comment1.dateCreated, comment5.dateCreated, 2, 0, false)

        then:
        result.recordCount == 5
        result.records.size() == 2
        result.records*.comment.containsAll([comment5, comment4]*.text) == true

        when: "the second page is requested"
        result = service.recentComments(opus1, comment1.dateCreated, comment5.dateCreated, 2, 2, false)

        then:
        result.recordCount == 5
        result.records.size() == 2
        result.records*.comment.containsAll([comment3, comment2]*.text) == true
    }
}
