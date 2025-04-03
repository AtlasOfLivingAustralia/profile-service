/*
 * Use this script to load missing images while keeping image metadata the same i.e. in situations where image is missing
 * from local storage but metadata is available in the database. Therefore, image should have the same imageId.
 */

const fs = require('fs');
const fsp = require('fs/promises');
const FormData = require('form-data');
const csv = require('csv-parser');
const axios = require('axios').default;
const yargs = require('yargs/yargs');
const { hideBin } = require('yargs/helpers');

const LOG_FILE = `./load-${Date.now()}.log`;
const log = async (message, ...optionalParams) => {
    await fsp.appendFile(LOG_FILE, `${message}\n`);
    console.log(message, ...optionalParams);
};

const imageDir = '/Users/var03f/Documents/ala/profile/data recovery/0_Common images Multiple Profiles';
const hostName = 'http://localhost:8098';
const inputImageFile = './missing-mangroves-files.csv';
const startLine = 0;
async function uploadImage(image, cookie) {
    const fileName = `${imageDir}/${image['originalFileName']}`
    if (!fs.existsSync(fileName)) {
        console.log(`Image file not found: ${fileName}`);
        return;
    }

    // Create a new FormData Instance
    const form = new FormData();
    console.log("imageId " + image.imageId);
    // Append form attributes
    form.append('opusId', image['opusUUID']);
    form.append('profileId', image['profileUUID']);
    form.append('dataResourceId', image['dataResourceId']);
    form.append('title', image['title']);
    form.append('imageId', image['imageId']);

    // Append the file to the form
    form.append(
        'file',
        fs.createReadStream(fileName),
        image['originalFileName']
    );

    // Construct the API url
    const URL = `${hostName}/opus/${image['opusUUID']}/profile/${encodeURIComponent(image['profileUUID'])}/image/upload`;

    let response;
    try {
        response = await axios.post(URL, form, {
            headers: {
                ...form.getHeaders(),
                cookie,
            },
        });
    } catch (error) {
        if (error?.response?.status === 403) {
            await log('Cookie is invalid!');
            throw new Error('Cookie is invalid!');
        }

        await log(
            `Image upload failed (${
                error?.response?.status || 'Unknown Error'
            })\n${URL}`
        );
        await log(JSON.stringify(image, null, 2));
        if (!error?.response?.status) {
            await log(error.message);
        }
    }

    // If the response data is a string, it also means the request was unsuccessful.
    if (
        (typeof response?.data === 'string' &&
            response?.data.includes('Sign in to the ALA')) ||
        response?.status === 403
    ) {
        await log('Cookie is invalid!');
        throw new Error('Cookie is invalid!');
    }

    return image;
}

(async () => {
    var rows = [];
    // Read the cookie from the cookie file
    const cookie = await fsp.readFile('./cookie.txt', 'utf8');
    fs.createReadStream(inputImageFile).pipe(csv()).on('data', async (row) => {
        try {
            rows.push(row);
        } catch (error) {
            console.error(`Error uploading image: ${row.imageId}`, error);
        }
    }).on('end', async () => {
        console.log('Finished reading the CSV.');
        rows.sort ((a, b) => a.imageId.localeCompare(b.imageId));
        for (var index = startLine || 0; index < rows.length; index++) {
            console.log(`Index: ${index}`);
            await uploadImage(rows[index], cookie);
        }
    });

    // Start bulk loading the images
    // await uploadImage({
    //     opusUUID: "4bba8441-0d60-46ae-bc48-2286ddf565cc",
    //     profileUUID: "86756fa9-8541-48c7-8588-2dada159bb38",
    //     imageId: "73f01be0-38fd-4545-a61e-f21e60607280",
    //     originalFileName: "dermsp5.jpg",
    //     dataResourceId: "dr27406",
    //     title: "Sodwana, north coast KwaZulu-Natal, SOUTH AFRICA. - 10m, August 2000, Size:  20mm. Photo: Valda Fraser"
    // }, cookie);
})();