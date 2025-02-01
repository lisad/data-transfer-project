# Google
This folder contains the extension implementation for the
[Google](https://www.google.com).

## Data Supported

 - Blogger Import of Social Posts.
 - Calendar Import & Export
 - Contacts Import & Export
 - G+ export of posts
 - Mail Import & Export
 - Photos Import & Export
 - Tasks Import & Export
 - Social Posts Export

## Current State

 - Calendar doesn't support recurring events yet
 - All services still need some final testing
 - G+ exports are limited by what is avalible in their API:
   - Export is only for public posts
   - For posts with multiple albums only the thumbnails are ported
 - Blogger import:
    - Imports into your first blog, doesn't create a new blog for the imported content
    - Stores attached photos in Drive

## Keys & Auth
Google uses OAuth 2 for authorization.

### Create a developer account
Before getting started you will need a [Google Cloud developer account](https://console.developers.google.com/start)

### Google Photos
#### Getting Started
 - Enable the Google Photos API and request an OAuth 2.0 client ID by following the [getting started guide](https://developers.google.com/photos/library/guides/get-started).
 - Choose which [authorization scopes](https://developers.google.com/photos/library/guides/authentication-authorization) your application will need. You can see the required scopes in the [GoogleOauthConfig](https://github.com/google/data-transfer-project/blob/master/extensions/auth/portability-auth-google/src/main/java/org/datatransferproject/auth/google/GoogleOAuthConfig.java#L51-L77).
 - Initially, your application will show an "Unverified app" message until it is [verified](https://support.google.com/cloud/answer/7454865) and you will have limited [API](https://developers.google.com/photos/library/guides/api-limits-quotas) and [OAuth](https://console.developers.google.com/apis/credentials/consent) quota
 - Your callback needs to be set to `https://<yourdomain>/callback/google`, even if that domain is `localhost`
 - We recommend you review the [UX Guidelines](https://developers.google.com/photos/library/guides/ux-guidelines) and [Acceptable use policy](https://developers.google.com/photos/library/guides/acceptable-use)  while designing your app if you want to eventually get increased quota through the Google Photos Partner Program.

#### Launching to Production
Before launching your application to production, you’ll want to get it verified and increase your quota limits by applying for the [Google Photos Partner Program](https://developers.google.com/photos/partner-program/overview). We recommend getting this process started well before your intended launch

1. Getting your app verified
 - Follow the [instructions](https://support.google.com/cloud/answer/7454865) under “Verification for apps”. **Note**: The OAuth consent screen requires a support email. This has to be a google account of a developer or a google group.
 - Visit the [OAuth API verification FAQs](https://support.google.com/cloud/answer/9110914) for more information.

2. Increasing Quota

*Oauth*
 - You can monitor your Oauth token grant rate [here](https://console.developers.google.com/apis/credentials/consent).
 - To increase your quota, fill out this [form](https://support.google.com/code/contact/oauth_quota_increase).

*API*
 - You can monitor your API quota [here](https://console.developers.google.com/iam-admin/quotas)
 - To increase your quota, apply for the [Google Photos Partner Program](https://developers.google.com/photos/partner-program/overview). **Note**: Your application must adhere to the [UX Guidelines](https://developers.google.com/photos/library/guides/ux-guidelines) and the [Acceptable use policy](https://developers.google.com/photos/library/guides/acceptable-use).
 - As part of your application you must pass an [integration review](https://developers.google.com/photos/partner-program/overview#launching) before launch, which could take several weeks to complete.

#### Best Practices
Some Google Photos API [best practices](https://developers.google.com/photos/library/guides/best-practices) are handled by the DTP infrastructure, but may require configuring:
 - __Authentication and authorization__ - only request the scopes required for the [specific adapter](https://github.com/google/data-transfer-project/blob/master/extensions/auth/portability-auth-google/src/main/java/org/datatransferproject/auth/google/GoogleOAuthConfig.java#L51-L77) you intend to use.
 - __Retrying Failed Requests__ - there is a [default retry strategy](https://github.com/google/data-transfer-project/blob/master/distributions/demo-server/src/main/resources/config/retry/default.yaml) built into the platform that is configurable. 
 - __Exponential backoff__ - we rate limit the writes by default to 1/sec and employ exponential backoff for retries

For more information about the Google Photos API, please visit their developer page.

## Maintained By

The Google extension was created and maintained by the
[DTP maintainers](mailto:portability-maintainers@googlegroups.com)
(this includes developers from Google).
