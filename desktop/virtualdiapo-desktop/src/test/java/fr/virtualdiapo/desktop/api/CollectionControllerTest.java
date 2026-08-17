package fr.virtualdiapo.desktop.api;

import fr.virtualdiapo.desktop.VirtualDiapoApplication;
import fr.virtualdiapo.desktop.catalog.JpegCollectionImporter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = VirtualDiapoApplication.class)
@AutoConfigureMockMvc
class CollectionControllerTest {
    private static final Path DATA_DIRECTORY = createDataDirectory();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JpegCollectionImporter importer;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("virtualdiapo.data-directory", DATA_DIRECTORY::toString);
        registry.add("virtualdiapo.image-directory", () -> DATA_DIRECTORY.resolve("images").toString());
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATA_DIRECTORY.resolve("test.db"));
    }

    @Test
    void importsPersistsAndServesAJpegCollection() throws Exception {
        var image = new MockMultipartFile("images", "lac.jpg", "image/jpeg", jpegBytes());

        var result = mvc.perform(multipart("/api/v1/collections")
                        .file(image)
                        .param("title", "Vacances 2026")
                        .param("description", "Le lac")
                        .param("year", "2026"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Vacances 2026"))
                .andExpect(jsonPath("$.slides.length()").value(1))
                .andExpect(jsonPath("$.slides[0].imagePath").isNotEmpty())
                .andReturn();

        var imagePath = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.slides[0].imagePath").toString();
        var collectionId = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id").toString();
        var slideId = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.slides[0].id").toString();

        mvc.perform(get("/api/v1/collections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Vacances 2026"))
                .andExpect(jsonPath("$[0].slideCount").value(1));

        mvc.perform(get(imagePath))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/jpeg"))
                .andExpect(header().exists("Cache-Control"))
                .andExpect(content().bytes(jpegBytes()));

        var addedImage = DATA_DIRECTORY.resolve("nouvelle.jpg");
        Files.write(addedImage, jpegBytes());
        importer.updateFiles(UUID.fromString(collectionId), "Vacances 2026", "Le lac", 2026,
                List.of(addedImage, importer.storedImagePath(UUID.fromString(slideId))));

        mvc.perform(get("/api/v1/collections/{id}", collectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slides.length()").value(2))
                .andExpect(jsonPath("$.slides[0].position").value(0))
                .andExpect(jsonPath("$.slides[1].id").value(slideId));

        mvc.perform(put("/api/v1/collections/{id}", collectionId)
                        .contentType("application/json")
                        .content("{\"title\":\"Vacances renommées\",\"description\":\"Nouvelle description\",\"year\":2027}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Vacances renommées"))
                .andExpect(jsonPath("$.slides.length()").value(2));

        mvc.perform(delete("/api/v1/collections/{id}", collectionId))
                .andExpect(status().isNoContent());
        mvc.perform(get(imagePath)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsAFileThatIsNotReallyAJpeg() throws Exception {
        var fake = new MockMultipartFile("images", "fake.jpg", "image/jpeg", "not an image".getBytes());

        mvc.perform(multipart("/api/v1/collections").file(fake).param("title", "Invalide"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le fichier fake.jpg n'est pas une image JPEG"));
    }

    private static byte[] jpegBytes() throws IOException {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.ORANGE.getRGB());
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }

    private static Path createDataDirectory() {
        try {
            return Files.createTempDirectory("virtualdiapo-test-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
