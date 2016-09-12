package au.org.ala.profile

import au.ala.org.ws.security.RequireApiKey
import au.ala.org.ws.security.SkipApiKeyCheck
import au.org.ala.profile.util.Utils
import grails.converters.JSON
import groovy.json.JsonSlurper
import org.apache.commons.httpclient.HttpStatus
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest
import org.springframework.web.multipart.commons.CommonsMultipartFile

@RequireApiKey
class ProfileController extends BaseController {

    ProfileService profileService
    BieService bieService
    AttachmentService attachmentService

    def saveBHLLinks() {
        def json = request.getJSON()

        if (!json) {
            badRequest()
        } else {
            boolean success = profileService.saveBHLLinks(json.profileId, json)

            render([success: success] as JSON)
        }
    }

    def saveLinks() {
        def json = request.getJSON()

        if (!json) {
            badRequest()
        } else {
            boolean success = profileService.saveLinks(json.profileId, json)

            render([success: success] as JSON)
        }
    }

    def saveAuthorship() {
        def json = request.getJSON()

        if (!params.profileId || !json) {
            badRequest()
        } else {
            boolean saved = profileService.saveAuthorship(params.profileId, json)

            if (saved) {
                render([success: saved] as JSON)
            } else {
                saveFailed()
            }
        }
    }

    def savePublication() {
        MultipartFile file = null
        if (request instanceof MultipartHttpServletRequest) {
            file = request.getFile("file0")
        }

        if (!file || !params.profileId) {
            badRequest()
        } else {
            Profile profile = getProfile()
            if (!profile) {
                notFound "Profile ${params.profileId} not found"
            } else {
                def result = profileService.savePublication(profile.uuid, file)
                if (result.error) {
                    int code = HttpStatus.SC_BAD_REQUEST
                    if (result.errorCode instanceof Integer && HttpStatus.getStatusText(result.errorCode)) {
                        code = result.errorCode
                    }
                    sendError code, result.error
                }

                render result as JSON
            }
        }
    }

    def getAttachmentMetadata() {
        if (!params.opusId || !params.profileId) {
            badRequest "opusId and profileId are required parameters"
        } else {
            Profile profile = getProfile()

            if (!profile) {
                notFound "No profile exists for id ${params.profileId}"
            } else {
                def profileOrDraft = (params.latest == "true" && profile.draft) ? profile.draft : profile
                if (params.attachmentId) {
                    Attachment attachment = profileOrDraft.attachments?.find { it.uuid == params.attachmentId }

                    if (!attachment) {
                        notFound "No attachment exists in profile ${params.profileId} with id ${params.attachmentId}"
                    } else {
                        attachment.downloadUrl = "${grailsApplication.config.grails.serverURL}/opus/${profile.opus.uuid}/profile/${profile.uuid}/attachment/${attachment.uuid}/download"
                        render ([attachment] as JSON)
                    }
                } else {
                    List<Attachment> attachments = profileOrDraft.attachments
                    attachments?.each {
                        // attachments may have a local file associated with them (e.g. pdf), or they may be a link to an
                        // external resource. If they have a local file, then the filename property will be set. If they
                        // are an external link, then the url property will be set.
                        if (it.filename) {
                            it.downloadUrl = "${grailsApplication.config.grails.serverURL}/opus/${profile.opus.uuid}/profile/${profile.uuid}/attachment/${it.uuid}/download"
                        }
                    }

                    render attachments as JSON
                }
            }
        }
    }

    @SkipApiKeyCheck
    def downloadAttachment() {
        if (!params.opusId || !params.profileId || !params.attachmentId) {
            badRequest "opusId, profileId and attachmentId are required parameters"
        } else {
            Profile profile = getProfile()

            if (!profile) {
                notFound "No profile was found for id ${params.profileId}"
            } else {
                def profileOrDraft = (params.latest == "true" && profile.draft) ? profile.draft : profile
                Attachment attachment = profileOrDraft.attachments.find { it.uuid == params.attachmentId }
                File file = null
                if (attachment) {
                    file = attachmentService.getAttachment(profile.opus.uuid, profile.uuid, params.attachmentId, Utils.getFileExtension(attachment.filename))
                }

                if (!file) {
                    notFound "No attachment was found with id ${params.attachmentId}"
                } else {
                    response.setContentType(attachment.contentType ?: "application/pdf")
                    response.setHeader("Content-disposition", "attachment;filename=${attachment.filename ?: 'attachment.pdf'}")
                    response.outputStream << file.newInputStream()
                    response.outputStream.flush()
                }
            }
        }
    }

    def saveAttachment() {
        if (!params.opusId || !params.profileId || !request.getParameter("data")) {
            badRequest "opusId and profileId are required parameters, and a JSON post body must be provided"
        } else {
            Profile profile = getProfile()

            if (!profile) {
                notFound()
            } else {
                Map metadata = new JsonSlurper().parseText(request.getParameter("data"))

                CommonsMultipartFile file = null
                if (request instanceof MultipartHttpServletRequest) {
                    file = request.getFile(request.fileNames[0])
                }
                List<Attachment> attachments = profileService.saveAttachment(profile.uuid, metadata, file)

                render attachments as JSON
            }
        }
    }

    def deleteAttachment() {
        if (!params.opusId || !params.profileId || !params.attachmentId) {
            badRequest "opusId, profileId and attachmentId are required parameters"
        } else {
            Profile profile = getProfile()

            if (!profile) {
                notFound()
            } else {
                profileService.deleteAttachment(profile.uuid, params.attachmentId)

                render([success: true] as JSON)
            }
        }
    }

    @SkipApiKeyCheck
    def getPublicationFile() {
        if (!params.publicationId) {
            badRequest "publicationId is a required parameter"
        } else {
            File file = profileService.getPublicationFile(params.publicationId)
            String contentType = Utils.getFileExtension(file.getName())
            if (!file) {
                notFound "The requested file could not be found"
            } else {
                response.setContentType("application/${contentType}")
                response.setHeader("Content-disposition", "attachment;filename=publication.${contentType}")
                response.setContentLength((int)file.length())
                file.withInputStream { InputStream is ->
                    response.outputStream << is
                }
            }
        }
    }

    def listPublications() {
        if (!params.profileId) {
            badRequest()
        } else {
            Profile profile = getProfile()
            if (!profile) {
                notFound "Profile ${params.profileId} not found"
            } else {
                Set<Publication> publications = profileService.listPublications(profile.uuid)

                render publications as JSON
            }
        }
    }

    def getPublicationDetails() {
        if (!params.publicationId) {
            badRequest()
        } else {
            Profile profile = profileService.getProfileFromPubId(params.publicationId);
            render text: [
                    profileId     : profile.uuid,
                    opusId        : profile.opus.uuid,
                    scientificName: profile.scientificName,
                    publications  : profile.publications
            ] as JSON
        }
    }

    def classification() {
        if (!params.guid || !params.opusId) {
            badRequest "GUID and OpusId are required parameters"
        } else {
            log.debug("Retrieving classification for ${params.guid} in opus ${params.opusId}")

            def classification = bieService.getClassification(params.guid)

            Opus opus = getOpus()

            if (!opus) {
                notFound "No matching Opus was found"
            } else {
                classification.each {
                    def profile = Profile.findByGuidAndOpus(it.guid, opus)
                    it.profileUuid = profile?.uuid ?: ''
                    it.profileName = profile?.scientificName
                }
            }

            response.setContentType("application/json")
            render classification as JSON
        }
    }

    def checkName() {
        if (!params.opusId || !params.scientificName) {
            badRequest "opusId and scientificName are required parameters"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound "No opus found for ${params.opusId}"
            } else {
                Map result = profileService.checkName(opus.uuid, params.scientificName as String)

                render result as JSON
            }
        }
    }

    def createProfile() {
        def json = request.getJSON()

        if (!json || !json.scientificName || !json.opusId) {
            badRequest "A json body with at least the scientificName and an opus id is required"
        } else {
            Opus opus = getOpus()
            if (!opus) {
                notFound "No matching opus can be found"
            } else {
                Profile profile = Profile.findByScientificNameAndOpus(json.scientificName, opus)

                if (profile) {
                    badRequest "A profile already exists for ${json.scientificName}"
                } else {
                    profile = profileService.createProfile(opus.uuid, json);
                    render profile as JSON
                }
            }
        }
    }

    def duplicateProfile() {
        def json = request.getJSON()

        if (!params.profileId || !json || !json.scientificName || !json.opusId) {
            badRequest "The profileId parameter and a json body with at least the scientificName and an opus id are required"
        } else {
            Opus opus = getOpus()
            if (!opus) {
                notFound "No matching opus can be found"
            } else {
                Profile profile = Profile.findByScientificNameAndOpus(json.scientificName, opus)

                if (profile) {
                    badRequest "A profile already exists for ${json.scientificName}"
                } else {
                    Profile sourceProfile = getProfile()
                    if (sourceProfile) {
                        profile = profileService.duplicateProfile(opus.uuid, sourceProfile, json);
                        render profile as JSON
                    } else {
                        notFound "No existing profile with id ${params.profileId} was found to duplicate"
                    }
                }
            }
        }
    }

    def renameProfile() {
        def json = request.getJSON()

        if (!json || !params.opusId || !params.profileId) {
            badRequest()
        } else {
            Profile profile = getProfile()

            if (!profile) {
                notFound()
            } else {
                profileService.renameProfile(profile.uuid, json)

                profile = getProfile()

                if (profile && profile.draft && params.latest == "true") {
                    Opus opus = profile.opus
                    profile = new Profile(profile.draft.properties)
                    profile.attributes?.each { it.profile = profile }
                    profile.opus = opus
                    profile.privateMode = true
                }

                render profile as JSON
            }
        }
    }

    def updateProfile() {
        def json = request.getJSON()

        if (!json || !params.profileId) {
            badRequest()
        } else {
            Profile profile = getProfile()

            if (!profile) {
                notFound()
            } else {
                profileService.updateProfile(profile.uuid, json)

                profile = getProfile()

                if (profile && profile.draft && params.latest == "true") {
                    Opus opus = profile.opus
                    profile = new Profile(profile.draft.properties)
                    profile.attributes?.each { it.profile = profile }
                    profile.opus = opus
                    profile.privateMode = true
                }

                render profile as JSON
            }
        }
    }

    def toggleDraftMode() {
        if (!params.profileId) {
            badRequest()
        } else {
            Profile profile = getProfile()

            if (!profile) {
                notFound()
            } else {
                boolean publish = params.publish == "true"
                profileService.toggleDraftMode(profile.uuid, publish)

                render([success: true] as JSON)
            }
        }
    }

    def discardDraftChanges() {
        if (!params.profileId) {
            badRequest()
        } else {
            Profile profile = getProfile()

            if (!profile) {
                notFound()
            } else {
                profileService.discardDraftChanges(profile.uuid)

                render([success: true] as JSON)
            }
        }
    }

    def getByUuid() {
        log.debug("Fetching profile by profileId ${params.profileId}")
        Profile profile = getProfile()

        if (profile) {
            if (profile && profile.draft && params.latest == "true") {
                Opus opus = profile.opus
                profile = new Profile(profile.draft.properties)
                profile.attributes?.each { it.profile = profile }
                profile.opus = opus
                profile.privateMode = true
            }

            render profile as JSON
        } else {
            notFound()
        }
    }

    def deleteProfile() {
        if (!params.profileId) {
            badRequest "profileId is a required parameter"
        } else {
            Profile profile = getProfile()
            if (!profile) {
                notFound "Profile ${params.profileId} not found"
            } else {
                boolean success = profileService.deleteProfile(profile.uuid)

                render([success: success] as JSON)
            }
        }
    }

    def archiveProfile() {
        def json = request.getJSON()
        if (!params.profileId || !json?.archiveComment) {
            badRequest "profileId and archiveComment are required parameters"
        } else {
            Profile profile = getProfile()

            if (!profile) {
                notFound "Profile ${params.profileId} not found"
            } else {
                Profile archive = profileService.archiveProfile(profile.uuid, json.archiveComment)

                render archive as JSON
            }
        }
    }

    def restoreArchivedProfile() {
        def json = request.getJSON()

        if (!params.profileId) {
            badRequest "profileId is a required parameters"
        } else {
            Profile profile = profileService.restoreArchivedProfile(params.profileId, json?.newName ?: null)

            render profile as JSON
        }
    }

    def recordStagedImage() {
        def json = request.getJSON()

        if (!params.profileId || !json) {
            badRequest "profileId and a json body are required"
        } else {
            Profile profile = getProfile()

            boolean success = profileService.recordStagedImage(profile.uuid, json)

            render([success: success] as JSON)
        }
    }

    def recordPrivateImage() {
        def json = request.getJSON()

        if (!params.profileId || !json) {
            badRequest "profileId and a json body are required"
        } else {
            Profile profile = getProfile()

            boolean success = profileService.recordPrivateImage(profile.uuid, json)

            render([success: success] as JSON)
        }
    }

    def listDocuments() {
        boolean editMode =  params?.editMode == "true"
        Profile profile = getProfile()
        if (!profile) {
            notFound()
        } else {
            def result = profileService.listDocument(profile, editMode)
            render result as JSON
        }
    }

    def deleteDocument(String id) {

        Profile profile = getProfile()

        if (!profile) {
            notFound()
        } else {
            def result
            def message
            boolean destroy = params.destroy == null ? false : params.destroy.toBoolean()
            result = profileService.deleteDocument(profile.uuid, id, destroy)
            message = [message: 'deleted', documentId: result.documentId, url: result.url]
            if (result.status == 'ok') {
                response.status = 200
                render message as JSON
            } else {
                log.error result.error
                render status: 400, text: result.error
            }
        }
    }

    /**
     * Creates or updates a Document object via an HTTP multipart request.
     * This method currently expects:
     * 1) For an updateDocument, the document ID should be in the URL path.
     * 2) The document metadata is supplied (JSON encoded) as the value of the
     * "document" HTTP parameter.  To createDocument a text file from a supplied string, supply the filename and content
     * as JSON properties.
     * 3) The file contents to be supplied as the value of the "files" HTTP parameter.  This is optional for
     * an updateDocument.
     * @param id The ID of an existing document to updateDocument.  If not present, a new Document will be created.
     */
    def updateDocument(String id) {
        log.debug("Updating ID ${id}")

        def props = request.JSON
        def result
        def message

        Profile profile = getProfile()

        if (!profile) {
            notFound()
        } else {

            if (id) {
                result = profileService.updateDocument(profile, props, id)
                message = [message: 'updated', documentId: result.documentId, url: result.url]
            } else {
                result = profileService.createDocument(profile, props)
                message = [message: 'created', documentId: result.documentId, url: result.url]
            }

            if (result.status == 'ok') {
                response.status = 200
                render message as JSON
            } else {
                //Document.withSession { session -> session.clear() }
                log.error result.error
                render status: 400, text: result.error
            }
        }
    }

    def setPrimaryMultimedia(String id) {
        log.debug("Updating ID ${id}")

        def props = request.JSON

        Profile profile = getProfile()

        if (!profile) {
            notFound()
        } else {

            def result = profileService.setPrimaryMultimedia(profile, props)

            if (result) {
                response.sendError(204)
            } else {
                log.error "Couldn't update $profile primary multimedia with $props"
                response.sendError(500)
            }
        }
    }
}
