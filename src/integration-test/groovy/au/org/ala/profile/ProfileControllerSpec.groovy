package au.org.ala.profile


import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import grails.util.GrailsWebMockUtil
import org.grails.plugins.testing.GrailsMockHttpServletRequest
import org.grails.plugins.testing.GrailsMockHttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.WebApplicationContext

@Integration
@Rollback
class ProfileControllerSpec extends BaseIntegrationSpec {

    @Autowired
    ProfileController controller

    @Autowired
    WebApplicationContext ctx

    ProfileService profileService

    def setup() {
        GrailsMockHttpServletRequest grailsMockHttpServletRequest = new GrailsMockHttpServletRequest()
        GrailsMockHttpServletResponse grailsMockHttpServletResponse = new GrailsMockHttpServletResponse()
        GrailsWebMockUtil.bindMockWebRequest(ctx, grailsMockHttpServletRequest, grailsMockHttpServletResponse)

        profileService = Mock(ProfileService)
        controller.profileService = profileService
        controller.attachmentService = Mock(AttachmentService)
    }

    def "updateProfile should return the draft profile if it exists and the 'latest' query param is 'true'"() {
        given:
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        Profile profile = save new Profile(scientificName: "sciName", opus: opus, draft: [uuid: "123", scientificName: "draftSciName"])

        when: "the latest param is 'true'"
        controller.request.json = "{'a': 'b'}"
        controller.params.profileId = profile.uuid
        controller.params.opusId = "opusId"
        controller.params.latest = "true"
        controller.updateProfile()

        then: "return the draft version"
        controller.response.status == 200
        controller.response.json.uuid == "123"
        controller.response.json.scientificName == "draftSciName"
    }

    def "updateProfile should return the public version of the profile if a draft exists but the 'latest' query param is not 'true'"() {
        given:
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        Profile profile = save new Profile(scientificName: "sciName", opus: opus, draft: [uuid: "123", scientificName: "draftSciName"])

        when: "the latest param is 'true'"
        controller.request.json = "{'a': 'b'}"
        controller.params.profileId = profile.uuid
        controller.params.opusId = "opusId"
        controller.updateProfile()

        then: "return the draft version"
        controller.response.status == 200
        controller.response.json.uuid == profile.uuid
        controller.response.json.scientificName == "sciName"
    }

    def "updateProfile should return the public profile if no draft exists, even if the 'latest' query param is 'true'"() {
        given:
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        Profile profile = save new Profile(scientificName: "sciName", opus: opus)

        when: "the latest param is 'true'"
        controller.request.json = "{'a': 'b'}"
        controller.params.profileId = profile.uuid
        controller.params.opusId = "opusId"
        controller.params.latest = "true"
        controller.updateProfile()

        then: "return the draft version"
        controller.response.status == 200
        controller.response.json.uuid == profile.uuid
        controller.response.json.scientificName == "sciName"
    }

    def "getByUuid should return the draft profile if it exists and the 'latest' query param is 'true'"() {
        given:
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        Profile profile = save new Profile(scientificName: "sciName", opus: opus, draft: [uuid: "123", scientificName: "draftSciName"])

        when: "the latest param is 'true'"
        controller.request.json = "{'a': 'b'}"
        controller.params.profileId = profile.uuid
        controller.params.opusId = "opusId"
        controller.params.latest = "true"
        controller.getByUuid()

        then: "return the draft version"
        controller.response.status == 200
        controller.response.json.uuid == "123"
        controller.response.json.scientificName == "draftSciName"
    }

    def "getByUuid should return the public version of the profile if a draft exists but the 'latest' query param is not 'true'"() {
        given:
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        Profile profile = save new Profile(scientificName: "sciName", opus: opus, draft: [uuid: "123", scientificName: "draftSciName"])

        when: "the latest param is 'true'"
        controller.request.json = "{'a': 'b'}"
        controller.params.profileId = profile.uuid
        controller.params.opusId = "opusId"
        controller.getByUuid()

        then: "return the draft version"
        controller.response.status == 200
        controller.response.json.uuid == profile.uuid
        controller.response.json.scientificName == "sciName"
    }

    def "getByUuid should return the public profile if no draft exists, even if the 'latest' query param is 'true'"() {
        given:
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        Profile profile = save new Profile(scientificName: "sciName", opus: opus)

        when: "the latest param is 'true'"
        controller.request.json = "{'a': 'b'}"
        controller.params.profileId = profile.uuid
        controller.params.opusId = "opusId"
        controller.params.latest = "true"
        controller.getByUuid()

        then: "return the draft version"
        controller.response.status == 200
        controller.response.json.uuid == profile.uuid
        controller.response.json.scientificName == "sciName"
    }

    def "downloadAttachment should use the draft if there is one and latest = true"() {
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        Profile profile = save new Profile(scientificName: "sciName", opus: opus)
        profile.draft = new DraftProfile(uuid: profile.uuid, scientificName: profile.scientificName, attachments: [new Attachment(uuid: "1234")])
        save profile

        when:
        controller.params.latest = "true"
        controller.params.profileId = profile.uuid
        controller.params.opusId = "opusId"
        controller.params.attachmentId = "1234"
        controller.downloadAttachment()

        then:
        1 * controller.attachmentService.getAttachment("opusId", profile.uuid, "1234", _)
    }

    def "downloadAttachment should use the profile when latest = false even if there is a draft"() {
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        Profile profile = save new Profile(scientificName: "sciName", opus: opus, attachments: [new Attachment(uuid: "1234")])
        profile.draft = new DraftProfile(uuid: profile.uuid, scientificName: profile.scientificName, attachments: [])

        when:
        controller.params.latest = "false"
        controller.params.profileId = profile.uuid
        controller.params.opusId = "opusId"
        controller.params.attachmentId = "1234"
        controller.downloadAttachment()

        then:
        1 * controller.attachmentService.getAttachment("opusId", profile.uuid, "1234", _)
    }

    def "getProfiles should get all profiles in an opus"() {
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        Profile profile = save new Profile(scientificName: "sciName", opus: opus, rank: "subspecies", attachments: [new Attachment(uuid: "1234")])

        when:
        controller.params.opusId = "opusId"
        controller.params.pageSize = "10"
        controller.params.startIndex = "0"
        controller.params.sort = "scientificName"
        controller.params.order = "desc"
        controller.params.rankFilter = "species"
        controller.getProfiles()

        then:
        1 * controller.profileService.getProfiles(_, 10, 0, "scientificName", "desc", "species") >> [count: 1, profiles: [scientificName: "sciName", rank: "subspecies"]]
        controller.response.status == 200
        controller.response.json.count == 1
    }
}
