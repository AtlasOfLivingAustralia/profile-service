var profiles = db.getCollection('profile').find({'privateImages':{$exists: true}})

var privateImages = ['Profile id,Image id,Title,Description,Rights Holder,Rights,Licence,Creator,Original File Name,Created']

const escapeComma = function(data) {
    if (data.includes(',')) {
        return data.replace(/,/g,"");
    }
    return data;
}
profiles.forEach(p => p.privateImages.forEach(i => privateImages.push(p._id + "," + i.imageId + "," + escapeComma(i.title) + ","
    + escapeComma(i.description) + "," + escapeComma(i.rightsHolder) + "," + escapeComma(i.rights) + "," + escapeComma(i.licence) + "," + escapeComma(i.creator)
    + "," + escapeComma(i.originalFileName) + "," + i.created)));

privateImages.forEach(i => print(i));