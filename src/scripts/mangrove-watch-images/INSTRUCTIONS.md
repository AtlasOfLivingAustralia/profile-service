# MangroveWatch Image Upload

1. Install dependencies with `npm i`
2. Create a `cookie.txt` file in the root of this repository & enter your Profiles cookie
3. Create an `images` subdirectory in this repository & extract the <u>**main**</u> MangroveWatch images into it (`2023 IMAGES.zip`)
4. Create an `additional_images` subdirectory in this repository & extract the <u>**additional**</u> MangroveWatch images into it (`more World Mangrove images.zip`)
5. Run `node load --dataResourceId=<id>` to upload the images
   1. You can optionally use `--dev` to load images into the dev environment (i.e. `profiles-dev.ala.org.au`), defaults to production.
   2. You can optionally use `--start=<number>` to start loading from a specified image.
   3. You can optionally use `--opusId=<id>` to specify a different Opus ID.

**Example:** `node load --dataResourceId=dr4672 --dev`
