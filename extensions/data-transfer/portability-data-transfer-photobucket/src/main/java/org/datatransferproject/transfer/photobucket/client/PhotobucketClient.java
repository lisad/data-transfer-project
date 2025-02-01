/*
 * Copyright 2021 The Data Transfer Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.datatransferproject.transfer.photobucket.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import okhttp3.*;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.spi.cloud.storage.JobStore;
import org.datatransferproject.spi.transfer.types.CopyExceptionWithFailureReason;
import org.datatransferproject.transfer.photobucket.client.helper.ExceptionTransformer;
import org.datatransferproject.transfer.photobucket.client.helper.OkHttpClientWrapper;
import org.datatransferproject.transfer.photobucket.model.MediaModel;
import org.datatransferproject.transfer.photobucket.model.PhotobucketAlbum;
import org.datatransferproject.transfer.photobucket.model.error.*;
import org.datatransferproject.transfer.photobucket.model.response.gql.PhotobucketGQLResponse;
import org.datatransferproject.transfer.photobucket.model.ProcessingResult;
import org.datatransferproject.transfer.photobucket.model.response.rest.UploadMediaResponse;
import org.datatransferproject.transfer.photobucket.model.response.rest.UserStatsResponse;
import org.datatransferproject.types.common.models.photos.PhotoAlbum;
import org.datatransferproject.types.common.models.photos.PhotoModel;
import org.datatransferproject.types.common.models.videos.VideoAlbum;
import org.datatransferproject.types.common.models.videos.VideoModel;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.UUID;
import java.util.function.Function;

import static org.datatransferproject.transfer.photobucket.data.PhotobucketConstants.*;

public class PhotobucketClient {
  private final JobStore jobStore;
  private final UUID jobId;
  private String pbRootAlbumId;
  private final ObjectMapper objectMapper;
  private final SimpleDateFormat simpleDateFormat =
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
  private final OkHttpClientWrapper okHttpClientWrapper;
  private final Monitor monitor;
  private final ExceptionTransformer exceptionTransformer;

  public PhotobucketClient(
      UUID jobId,
      Monitor monitor,
      Credential credential,
      OkHttpClient httpClient,
      JobStore jobStore,
      ObjectMapper objectMapper,
      String requester) {
    this.jobId = jobId;
    this.jobStore = jobStore;
    this.monitor = monitor;
    this.objectMapper = objectMapper;
    this.okHttpClientWrapper = new OkHttpClientWrapper(jobId, credential, httpClient, requester);
    this.exceptionTransformer = new ExceptionTransformer(monitor);
  }

  public String createTopLevelAlbum(String name) throws CopyExceptionWithFailureReason {
    // in case if albumId was not found while migrating photos,
    // we will migrate them into this top level album to avoid data loss
    PhotoAlbum photoAlbum = new PhotoAlbum(jobId.toString(), name, "");
    return createAlbum(photoAlbum, "");
  }

  public String createAlbum(VideoAlbum videoAlbum, String namePrefix)
      throws CopyExceptionWithFailureReason {
    return createAlbum(
        new PhotoAlbum(videoAlbum.getId(), videoAlbum.getName(), videoAlbum.getDescription()),
        namePrefix);
  }

  public String createAlbum(PhotoAlbum photoAlbum, String namePrefix)
      throws CopyExceptionWithFailureReason {
    try {
      // check if album was not created before
      return getPBAlbumId(photoAlbum.getId());
    } catch (Exception exception) {
      try {
        // if album was not created
        // generate gql query for getting pb album id via rest call
        RequestBody requestBody = createAlbumGQLViaRestMutation(photoAlbum, namePrefix);
        // get pbAlbumId and save it into job store
        Function<Response, ProcessingResult> bodyTransformF =
            response -> {
              try {
                // get photobucket albumId from response
                String pbAlbumId = parseGQLResponse(response).getCreatedAlbumId();
                // add photobucket albumId to the internal store, to match photos with proper albums
                jobStore.create(jobId, photoAlbum.getId(), new PhotobucketAlbum(pbAlbumId));
                return new ProcessingResult(pbAlbumId);
              } catch (IOException | GraphQLException e) {
                return new ProcessingResult(
                    new AlbumImportException("Album was not created: " + e.getMessage()));
              }
            };

        // throw exception only if failed for top level album
        Function<Void, Boolean> isTopLevelAlbumF = v -> photoAlbum.getId().equals(jobId.toString());

        // fallback result in case if query execution/result parsing failed. In case if it's top
        // level
        // album, we need to provide null to throw an exception. If it's usual album we can proceed.
        ProcessingResult fallbackResult =
            isTopLevelAlbumF.apply(null) ? null : new ProcessingResult("");

        ProcessingResult result =
            okHttpClientWrapper.performGQLRequest(
                requestBody, bodyTransformF, isTopLevelAlbumF, fallbackResult);
        return result.extractOrThrow();
      } catch (Exception e) {
        throw exceptionTransformer.transformException(e);
      }
    }
  }

  public ProcessingResult uploadVideo(VideoModel videoModel)
      throws CopyExceptionWithFailureReason {
    String albumId = videoModel.getAlbumId() == null ? jobId.toString() : videoModel.getAlbumId();
    MediaModel mediaModel =
        new MediaModel(
            videoModel.getName(),
            videoModel.getContentUrl().toString(),
            videoModel.getDescription(),
            videoModel.getEncodingFormat(),
            albumId,
            videoModel.isInTempStore(),
            null);
    return uploadMedia(mediaModel, MAX_VIDEO_SIZE_IN_BYTES);
  }

  public ProcessingResult uploadPhoto(PhotoModel photoModel) throws CopyExceptionWithFailureReason {
    MediaModel mediaModel =
        new MediaModel(
            photoModel.getTitle(),
            photoModel.getFetchableUrl(),
            photoModel.getDescription(),
            photoModel.getMediaType(),
            photoModel.getAlbumId(),
            photoModel.isInTempStore(),
            photoModel.getUploadedTime());
    return uploadMedia(mediaModel, MAX_IMAGE_SIZE_IN_BYTES);
  }

  private String encodeQueryParam(String queryParam) throws UnsupportedEncodingException {
    return URLEncoder.encode(queryParam, StandardCharsets.UTF_8.toString());
  }

  private ProcessingResult uploadMedia(MediaModel mediaModel, long maxFileSizeInBytes)
      throws CopyExceptionWithFailureReason {
    try {
      RequestBody uploadRequestBody;
      // get pbAlbumId based on provided albumId
      String pbAlbumId = getPBAlbumId(mediaModel.getAlbumId());
      String url =
          String.format(
              "%s?albumId=%s&name=%s",
              UPLOAD_URL, encodeQueryParam(pbAlbumId), encodeQueryParam(mediaModel.getTitle()));
      String maybeUploadDate = extractUploadDate(mediaModel);
      // extract upload date either from provided metadata or from exif
      if (maybeUploadDate != null) {
        url = url + String.format("&uploadDate=%s", encodeQueryParam(maybeUploadDate));
      }
      InputStream inputStream;
      HttpURLConnection connection;
      long contentLength = -1;
      if (mediaModel.isInTempStore()) {
        // stream file from temp store
        monitor.debug(
            () ->
                (String.format(
                    "Getting stream from temp store for image url [%s]",
                    mediaModel.getFetchableUrl())));
        inputStream =
            new BufferedInputStream(
                jobStore.getStream(jobId, mediaModel.getFetchableUrl()).getStream());

      } else if (mediaModel.getFetchableUrl() != null) {
        // stream file from url
        monitor.debug(
            () ->
                (String.format(
                    "Getting stream by url store for image url [%s]",
                    mediaModel.getFetchableUrl())));
        connection = getConnection(mediaModel.getFetchableUrl());
        contentLength = connection.getContentLength();
        inputStream = connection.getInputStream();
      } else {
        throw new IllegalStateException(
            "Unable to get input stream for image " + mediaModel.getTitle());
      }

      uploadRequestBody =
          new InputStreamRequestBodyCustom(
              MediaType.parse(mediaModel.getMediaType()), inputStream, contentLength);

      if (isUserOveStorage(uploadRequestBody.contentLength())) {
        throw new OverlimitException();
      }

      if (uploadRequestBody.contentLength() > maxFileSizeInBytes) {
        throw new MediaFileIsTooLargeException(mediaModel.getTitle());
      }

      Function<Response, ProcessingResult> uploadResponseTransformationF =
          uploadImageResponse -> {
            // note: if 201 code was provided, but response value is empty, do not fail upload, just
            // skip
            // title/description update
            if (uploadImageResponse.body() != null
                && mediaModel.getDescription() != null
                && !mediaModel.getDescription().isEmpty()) {
              // get imageId from provided response
              String imageId;
              try {
                imageId =
                    objectMapper.readValue(
                            uploadImageResponse.body().string(), UploadMediaResponse.class)
                        .id;
              } catch (IOException ioException) {
                return new ProcessingResult(
                    "Partial success: image was uploaded, but metadata wasn't updated - body parsing exception");
              }
              String requestBodyString = String.format(
                      "{\"query\": \"mutation updateImageDTP($imageId: String!, $title: String!, $description: String){ updateImage(imageId: $imageId, title: $title, description: $description)}\", \"variables\": {\"imageId\": \"%s\", \"title\": \"%s\", \"description\": \"%s\"}}",
                      imageId, escapeSpecialCharacters(mediaModel.getTitle()), escapeSpecialCharacters(mediaModel.getDescription()));

              // update metadata gql query
              RequestBody updateMetadataRequestBody =
                  RequestBody.create(
                      MediaType.parse("application/json"),
                          requestBodyString
                      );

              // do not verify update metadata response
              Function<Response, ProcessingResult> updateMetadataTransformationF =
                  response -> new ProcessingResult(imageId);

              // newer fail in case of error
              Function<Void, Boolean> conditionalExceptionF = v -> true;

              try {
                // add metadata via gql
                return okHttpClientWrapper.performGQLRequest(
                    updateMetadataRequestBody,
                    updateMetadataTransformationF,
                    conditionalExceptionF,
                    null);
              } catch (Exception ignored) {
                return new ProcessingResult(
                    "Partial success: image was uploaded, but metadata wasn't updated - gql call failed");
              }
            } else {
              return new ProcessingResult(
                  "Partial success: image was uploaded, but metadata wasn't updated - body was empty");
            }
          };

      return okHttpClientWrapper.performRESTPostRequest(
          url, uploadRequestBody, uploadResponseTransformationF);
    } catch (Exception e) {
      throw exceptionTransformer.transformException(e);
    }
  }

  private String escapeSpecialCharacters(String str) {
    if (str != null) return StringEscapeUtils.escapeJava(str);
    return null;
  }

  /**
   * Create album either under pbRoot album (in case if we create top album) or under top album
   * TODO: add description while album creation, not supported for now within the same call
   */
  private RequestBody createAlbumGQLViaRestMutation(PhotoAlbum photoAlbum, String prefix)
      throws Exception {
    String pbParentId = getParentPBAlbumId(photoAlbum.getId());

    String jsonString =
        String.format(
            "{\"query\": \"mutation createAlbumDTP($title: String!, $parentAlbumId: String!){ createAlbum(title: $title, parentAlbumId: $parentAlbumId){ id }}\", \"variables\": {\"title\": \"%s\", \"parentAlbumId\": \"%s\"}}",
            prefix + escapeSpecialCharacters(photoAlbum.getName()), pbParentId);
    return RequestBody.create(MediaType.parse("application/json"), jsonString);
  }

  private String getParentPBAlbumId(String albumId) throws Exception {
    // for top level album parent is PB root album
    if (albumId.equals(jobId.toString())) {
      return getPbRootAlbumId();
    } else {
      try {
        return getPBAlbumId(albumId);
      } catch (Exception e) {
        // in case if pbAlbumId not found for current album, migrate photos to the top level album
        return getPBAlbumId(jobId.toString());
      }
    }
  }

  private String getPBAlbumId(String albumId) throws IOException, NullPointerException {
    return jobStore.findData(jobId, albumId, PhotobucketAlbum.class).getPbId();
  }

  private PhotobucketGQLResponse parseGQLResponse(Response response)
      throws GraphQLException, IOException {
    if (response.body() != null) {
      return objectMapper.readValue(response.body().string(), PhotobucketGQLResponse.class);
    } else {
      throw new GraphQLException("Empty response body was provided by GQL server");
    }
  }

  private String getPbRootAlbumId() throws Exception {
    // request if pbRootAlbumId was not requested yet
    if (pbRootAlbumId == null) {
      RequestBody requestBody =
          RequestBody.create(
              MediaType.parse("application/json"),
              "{\"query\": \"query getRootAlbumIdDTP{ getProfile{ defaultAlbum }}\"}");
      Function<Response, ProcessingResult> bodyTransformF =
          response -> {
            try {
              // get photobucket pbRootAlbumId from response
              pbRootAlbumId = parseGQLResponse(response).getRootAlbumId();
              return new ProcessingResult(pbRootAlbumId);
            } catch (Exception e) {
              return new ProcessingResult(
                  new ResponseParsingException(
                      "Unable to process GQL response to get root album id: " + e.getMessage()));
            }
          };

      // always fail in case of error, as unable to proceed without knowing pbRootId
      Function<Void, Boolean> conditionalExceptionF = v -> true;

      // fallback result is null, as unable to proceed without root
      ProcessingResult result =
          okHttpClientWrapper.performGQLRequest(
              requestBody, bodyTransformF, conditionalExceptionF, null);
      return result.extractOrThrow();

    } else {
      return pbRootAlbumId;
    }
  }

  private Boolean isUserOveStorage(long contentLength) throws Exception {
    // temporary disabled as new DTP users will ve moved to trial plan
    if (IS_OVER_STORAGE_VERIFICATION_ENABLED) {
      // make request and extract response body string
      Function<Response, ProcessingResult> bodyTransformF =
          response -> {
            try {
              return new ProcessingResult(response.body().string());
            } catch (NullPointerException | IOException e) {
              return new ProcessingResult(
                  new ResponseParsingException(
                      "Unable to process REST response to get user stats"));
            }
          };

      String requestResultBodyStr =
          okHttpClientWrapper
              .performRESTGetRequest(USER_STATS_URL, bodyTransformF)
              .extractOrThrow();
      UserStatsResponse stats =
          objectMapper.readValue(requestResultBodyStr, UserStatsResponse.class);
      return !((stats.availableSpace - contentLength >= 0) && (stats.availableImages - 1 >= 0));
    } else return false;
  }

  /**
   * @return normalized upload date, or null, based either on uploadDate PhotoModel field or on exif
   *     data
   */
  private String extractUploadDate(MediaModel mediaModel) {
    if (mediaModel.getUploadedTime() != null) {
      return simpleDateFormat.format(mediaModel.getUploadedTime());
    } else {
      try {
        final byte[] bytes =
            IOUtils.toByteArray(
                new BufferedInputStream(
                    jobStore.getStream(jobId, mediaModel.getFetchableUrl()).getStream()));
        final ImageMetadata metadata = Imaging.getMetadata(bytes);

        if (metadata == null) {
          return null;
        }

        final JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;

        final TiffImageMetadata exif = jpegMetadata.getExif();

        if (exif == null) {
          return null;
        }

        String[] values = exif.getFieldValue(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);

        if (values == null || values.length == 0) {
          values = exif.getFieldValue(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED);
        }

        if (values == null || values.length == 0) {
          return null;
        }

        return simpleDateFormat.format(simpleDateFormat.parse(values[0]));
      } catch (Exception e) {
        monitor.debug(() -> ("Unable to fetch media upload date from EXIF data"));
        return null;
      }
    }
  }

  /**
   * All timeouts are set for default as infinite
   *
   * @see java/net/URLConnection.java
   */
  private HttpURLConnection getConnection(String urlStr) throws IOException {
    URL url = new URL(urlStr);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.connect();
    return conn;
  }

  private static class InputStreamRequestBodyCustom extends RequestBody {
    private final InputStream inputStream;
    private final MediaType contentType;
    private final long contentLength;

    public InputStreamRequestBodyCustom(
        MediaType contentType, InputStream inputStream, long contentLength) {
      if (inputStream == null) throw new NullPointerException("inputStream == null");
      this.contentType = contentType;
      this.inputStream = inputStream;
      this.contentLength = contentLength;
    }

    @Nullable
    @Override
    public MediaType contentType() {
      return contentType;
    }

    @Override
    public long contentLength() throws IOException {
      if (contentLength == -1) {
        return inputStream.available() == 0 ? -1 : inputStream.available();
      }
      return contentLength;
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
      try (Source source = Okio.source(inputStream)) {
        sink.writeAll(source);
      }
    }
  }
}
