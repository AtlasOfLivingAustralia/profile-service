package au.org.ala.profile

import grails.converters.JSON

class ImageController extends BaseController {

    def localImageService

    def getImageInfo() {
        String imageId = params.imageId
        if (!imageId) {
            badRequest "imageId is a required parameter"
        } else {
            def image = localImageService.getImageInfo(imageId)
            render image as JSON
        }
    }

    def updateMetadata() {
        String imageId = params.imageId
        String opusId = params.opusId
        final metadata = request.getJSON()
        if (!opusId || !imageId || !metadata) {
            badRequest "opusId, imageId and request body are required parameters"
        } else {
            def image = localImageService.updateMetadata(imageId, metadata)
            render image as JSON
        }
    }

}
