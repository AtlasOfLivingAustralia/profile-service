const fs = require('fs');
const fsp = require('fs/promises');

const FormData = require('form-data');
const csv = require('csvtojson');
const axios = require('axios').default;
const yargs = require('yargs/yargs');
const { hideBin } = require('yargs/helpers');

const LOG_FILE = `./load-${Date.now()}.log`;

const log = async (message, ...optionalParams) => {
  await fsp.appendFile(LOG_FILE, `${message}\n`);
  console.log(message, ...optionalParams);
};

// Parameter parsing & check
const parameters = ['dataResourceId'];
const args = yargs(hideBin(process.argv)).argv;
for (const param of parameters) {
  if (!args[param]) {
    log(`Missing '${param}' parameter!`);
    return;
  }
}

async function uploadImage(image, cookie, imagesDir = 'images') {
  // Create a new FormData Instance
  const form = new FormData();

  // Append form attributes
  form.append('rightsHolder', 'MangroveWatch - NCDuke ©');
  form.append('dataResourceId', args.dataResourceId);
  form.append('creator', image['AUTHOR']);
  form.append('title', image['CAPTION']);
  form.append('rights', 'Fair use with acknowledgement of rights holder.');
  form.append(
    'licence',
    'Creative Commons Attribution-Noncommercial-Share Alike (International)'
  );

  // Append the file to the form
  form.append(
    'file',
    fs.createReadStream(`./${imagesDir}/${image['IMAGE FILENAME']}`),
    image['IMAGE FILENAME']
  );

  // Construct the API url
  const URL = `https://${
    args.dev ? 'profiles-dev' : 'profiles'
  }.ala.org.au/opus/${
    args.opusId || 'mangrovewatch'
  }/profile/${encodeURIComponent(image['Species Name'])}/image/upload`;

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

async function bulkUpload(cookie) {
  // Load species & images CSV
  let [species, images] = await Promise.all([
    await csv().fromFile('./data/mgwatch_species.csv'),
    await csv().fromFile('./data/mgwatch_images.csv'),
  ]);

  // Sort the species by the length of the code so that names aren't preliminarily matched
  // Ac_ebr,Acanthus ebracteatus
  // Ac_ebr-ebr,Acanthus ebracteatus subsp. ebracteatus
  species = species.sort(
    (a, b) => b['Taxa Code'].length - a['Taxa Code'].length
  );

  // Map each image to include the species name based on the taxon code
  let mapped = images.map((image) => ({
    ...image,
    'Species Name': species.find((species) =>
      image['IMAGE FILENAME']
        .toLowerCase()
        .startsWith(species['Taxa Code'].toLowerCase())
    )?.['Species Name'],
  }));

  // Sequentially upload each image
  for (
    let uploadIndex = args.start || 0;
    uploadIndex < mapped.length;
    uploadIndex++
  ) {
    const image = mapped[uploadIndex];

    await log(
      `Uploading image ${uploadIndex + 1}/${mapped.length}... (${
        image['Species Name']
      }, ${image['IMAGE FILENAME']})`
    );

    await uploadImage(image, cookie, 'images');
  }
}

(async () => {
  // Read the cookie from the cookie file
  const cookie = await fsp.readFile('./cookie.txt', 'utf8');

  // Start bulk loading the images
  await bulkUpload(cookie);
})();
