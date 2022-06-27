<div class="section" markdown="1">
## Technical Overview
<div class="mustache">
</div>

To get a more in-depth understanding of the project, its fundamentals and the details involved, please download [“Data Transfer Project Overview and Fundamentals”]({{site.baseurl}}/dtp-overview.pdf). You can also use the button at the top of the page.

<a href="{{site.baseurl}}/dtp-overview.pdf" class="download-link" ><img class="download-image" src="{{site.baseurl}}/images/download.png"></a>
</div>

<div class="section" markdown="1">
## How does DTP Work
<div class="mustache">
</div>

DTP comprises three main components:

### Data Models

Data Models are the canonical formats that establish a common understanding of how to transfer data.
Adapters provide a method for converting each Provider’s proprietary data and authentication formats into a form that is usable by the system.
Task Management Library provides the plumbing to power the system.
Data Models
Data Models represent the data when being transferred between two different companies. Ideally each company would use interoperable APIs (e.g. ActivityPub) to allow data to flow between them. However in many cases that is not the case. In those cases there needs to be a way to transfer the data from one companies representation to another companies representation.

Data Models are clustered together, typically by industry grouping, to form Verticals. A Provider could have data in one or more Verticals. Verticals could be photos, email, contacts, or music. Each Vertical has its own set of Data Models that enable seamless transfer of the relevant file types. For example, the Music vertical could have Data Models for music, playlists and videos.

Ideally, a Vertical will have a small number of well-defined and widely-adopted Data Models. In such a situation, the generally accepted standard will be used as the Data Model for that Vertical across companies. This is not currently the case for most Verticals because Data Models have emerged organically in a largely disconnected ecosystem.

One goal of DTP is to encourage organizations to use common Data Models in their systems, which will happen if organizations take importing and exporting data into consideration when initially designing their systems or providing updates. Using a common Data Model will significantly reduce the need for companies to maintain and update proprietary APIs.

See [current data models](https://github.com/google/data-transfer-project/tree/master/portability-types-common/src/main/java/org/datatransferproject/types/common/models).

### Company Specific Adapters

There are two main kinds of Adapters: Data Adapters and Authentication Adapters. These Adapters exist outside of a Provider’s core infrastructure and can be written either by the Provider itself, or by third parties that would like to enable data transfer to and from a Provider.

Data Adapters Data Adapters are pieces of code that translate a given Provider’s APIs into Data Models used by DTP. Data Adapters come in pairs: an exporter that translates from the Provider’s API into the Data Model, and an importer that translates from the Data Model into the Provider’s API.

Authentication Adapters Authentication Adapters are pieces of code that allow consumers to authenticate their accounts before transferring data out of or into another Provider. OAuth is likely to be the choice for most Providers, however DTP is agnostic to the type of authentication.

See [current Adapters](https://github.com/google/data-transfer-project/tree/master/extensions/data-transfer).

### Task Management

The rest is just plumbing.

The Task Management Libraries handle background tasks, such as calls between the two relevant Adapters, secure data storage, retry logic, rate limiting, pagination management, failure handling, and individual notifications.
DTP has developed a collection of Task Management Libraries as a reference implementation for how to utilize the Adapters to transfer data between two Providers. If preferred, Providers can choose to write their own implementation of the Task Management Libraries that utilize the Data Models and Adapters of DTP.

<div class="section" markdown="1">
## How to try it out
<div class="mustache">
</div>

DTP is still in development and is not quite ready for everyone to use yet. However if you want to give it a shot we have a couple methods you can try out:

Note: For both of these you need to get your own API keys from the services you want to port data between; please see instructions for getting keys.

  * [Via Docker](https://github.com/google/data-transfer-project/blob/master/Documentation/RunningLocally.md)
  * [Via Code](https://github.com/google/data-transfer-project/blob/master/Documentation/Developer.md)
  * Sites powered by DTP (coming soon)
</div>
