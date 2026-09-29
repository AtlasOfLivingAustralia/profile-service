package au.org.ala.profile

import au.org.ala.profile.util.Utils
import org.springframework.web.multipart.MultipartFile

class AttachmentService {

    static final long DEFAULT_MAX_SOUND_FILE_SIZE = 5_000_000L
    static final Map<String, Set<String>> SOUND_CONTENT_TYPES = [
            mp3: ['audio/mpeg', 'audio/mp3', 'audio/x-mp3'] as Set,
            wav: ['audio/wav', 'audio/x-wav', 'audio/wave', 'audio/vnd.wave'] as Set
    ]

    def grailsApplication

    boolean deleteAttachment(String opusId, String profileId, String attachmentId, String extension) {
        File file = new File(getPath(opusId, profileId, attachmentId, extension))

        file.delete()
    }

    void saveAttachment(String opusId, String profileId, String attachmentId, MultipartFile incomingFile, String extension) {
        File file = new File(getPath(opusId, profileId, attachmentId, extension))
        file.mkdirs()
        incomingFile.transferTo(file)
    }

    File getAttachment(String opusId, String profileId, String attachmentId, String extension) {
        File file = new File(getPath(opusId, profileId, attachmentId, extension))

        file.exists() ? file : null
    }

    String getPath(String opusId, String profileId, String attachmentId, String extension) {
        "${grailsApplication.config.attachments.directory}/${opusId}/${profileId ? profileId + '/' : ''}${attachmentId}.${extension}"
    }

    Map validateSound(MultipartFile incomingFile) {
        if (!incomingFile || incomingFile.empty) {
            return [valid: false, error: 'A sound file is required']
        }

        String extension = Utils.getFileExtension(incomingFile.originalFilename)?.toLowerCase()
        if (!SOUND_CONTENT_TYPES.containsKey(extension)) {
            return [valid: false, error: 'Invalid sound file extension - must be one of [mp3, wav]']
        }

        long maxFileSize = grailsApplication.config.getProperty('attachments.sound.maxFileSize', Long, DEFAULT_MAX_SOUND_FILE_SIZE)
        if (incomingFile.size > maxFileSize) {
            return [valid: false, error: "Sound file exceeds the maximum size of ${maxFileSize} bytes"]
        }

        String contentType = incomingFile.contentType?.toLowerCase()?.split(';')?.first()?.trim()
        if (!SOUND_CONTENT_TYPES[extension].contains(contentType)) {
            return [valid: false, error: "Invalid content type '${incomingFile.contentType}' for a ${extension} sound file"]
        }

        [valid: true, extension: extension, contentType: contentType]
    }

    Map<String,File> collectAllAttachmentsIncludingOriginalNames(Profile profile) {
        Map<String,File> fileMap = [:]
        if (profile.attachments) {
            List<Attachment> attachments = profile.getAttachments()
            attachments.each { attachment ->
                File file = getAttachment(profile.opus.uuid, profile.uuid, attachment.uuid, Utils.getFileExtension(attachment.filename))
                if (file) {
                    fileMap.put(attachment.getFilename(),file)
                }
            }
        }
        return fileMap
    }


}
