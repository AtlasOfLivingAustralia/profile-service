const fs = require('fs');
const fsp = require('fs/promises');

const FormData = require('form-data');
const csv = require('csvtojson');
const axios = require('axios').default;
const yargs = require('yargs/yargs');
const { hideBin } = require('yargs/helpers');

// Parameter parsing & check
const parameters = ['dataResourceId'];
const args = yargs(hideBin(process.argv)).argv;
for (const param of parameters) {
  if (!args[param]) {
    console.log(`Missing '${param}' parameter!`);
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
    console.log(
      `Image upload failed (${error?.response?.status || 'Unknown Error'})`,
      image
    );
  }

  // If the response data is a string, it also means the request was unsuccessful.
  if (
    (typeof response?.data === 'string' &&
      response?.data.includes('Sign in to the ALA')) ||
    response.status === 403
  ) {
    throw new Error('Cookie is invalid!');
  }

  return image;
}

async function bulkUpload(cookie) {
  // Load species & images CSV
  let [species, images, additional] = await Promise.all([
    await csv().fromFile('./data/mgwatch_species.csv'),
    await csv().fromFile('./data/mgwatch_images.csv'),
    await fsp.readdir('./additional_images'),
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
    'Species Name': encodeURIComponent(
      species.find((species) =>
        image['IMAGE FILENAME']
          .toLowerCase()
          .startsWith(species['Taxa Code'].toLowerCase())
      )?.['Species Name']
    ),
  }));

  // Map the additional images
  mapped = [
    ...mapped,
    ...additional.map((image) => {
      // Find the species name from the species codes CSV
      const speciesName = species.find((species) =>
        image.toLowerCase().startsWith(species['Taxa Code'].toLowerCase())
      )?.['Species Name'];

      // Generate an image entry to be uploaded
      return {
        _additional: true,
        AUTHOR: 'NCDuke',
        CAPTION: `Diagram for ${speciesName}`,
        'IMAGE FILENAME': image,
        'Species Name': speciesName || 'BLANK',
      };
    }),
  ];

  // Sequentially upload each image
  for (
    let uploadIndex = args.start || 0;
    uploadIndex < mapped.length;
    uploadIndex++
  ) {
    const image = mapped[uploadIndex];

    console.log(
      `Uploading image ${uploadIndex + 1}/${mapped.length}... (${
        image['Species Name']
      }, ${image['IMAGE FILENAME']})`
    );

    await uploadImage(
      image,
      cookie,
      image._additional ? 'additional_images' : 'images'
    );
  }
}

(async () => {
  // Read the cookie from the cookie file
  const cookie = await fsp.readFile('./cookie.txt', 'utf8');

  // Start bulk loading the images
  await bulkUpload(cookie);
})();
