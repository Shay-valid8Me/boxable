[Boxable](https://github.com/Shay-valid8Me/boxable/) - A java library to build tables in PDF documents.
=======


Forked from [Boxable](http://dhorions.github.io/boxable/). Boxable is a library that can be used to easily create tables in pdf documents.  It uses the [PDFBox](https://pdfbox.apache.org/) PDF library under the hood.

# Features

- Build tables in pdf documents
- Convert csv data into tables in pdf documents
- Convert Lists into tables in pdf documents

#### Boxable supports next tables features
- HTML tags in cell content (not all! `<p>,<i>,<b>,<br>,<ul>,<ol>,<li>`)
- Horizontal & Vertical Alignment of the text
- Images inside cells and outside table (image scale is also supported)
- basic set of rendering attributes for lines (borders)
- rotated text (by 90 degrees)
- writing text outside tables

# Gradle
``` 
implementation group: 'com.valid8me.opensource', name: 'boxable', version: "1.7.1-SNAPSHOT"
```
### Build Project

Run the following command `./gradlew build`

### Run Unit Tests

Run the following command `./gradlew clean test`

### Run Unit Tests with Code Coverage

Run the following command  `./gradlew clean test jacocoTestReport`

### Publishing Package
<!-- TODO: Update with valid8Me specific links -->
Provided you've correctly increased the version number as specified in the [PullRequest Checklist](.github/pull_request_template.md).
The [Github workflow](https://github.com/valid8me-engineering/boxable/actions/workflows/develop.yml) will take over this responsability and automatically build and publish the latest artifact for this shared library.

It should not be necessary, but you may be able to directly [publish action](https://github.com/valid8me-engineering/boxable/actions/workflows/publish-package.yml).

### Consuming Project From Other Repositories
Other projects can add the corresponding Github package as a Maven repository
in their build.gradle and declare a dependency on your library to use it.
See [How to Add a Java Library to Project](https://horizon8.atlassian.net/wiki/spaces/VALID8ME/pages/1354596361/How+to+Add+Java+Shared+Library+to+Project) (please note this guide was written specifically for the java-shared-libs project, but the steps will be very similar)



# Tutorial
A tutorial is being created and will be accessible at https://github.com/dhorions/boxable/wiki.
If you want to help, please let us know  [here](https://github.com/dhorions/boxable/issues/41).

# Usage examples

## Create a pdf from a csv file 

```java
String data = readData("https://s3.amazonaws.com/misc.quodlibet.be/Boxable/Eurostat_Immigration_Applications.csv");
BaseTable pdfTable = new BaseTable(yStart, yStartNewPage, bottomMargin, tableWidth, margin, doc, page, true,true);
DataTable t = new DataTable(pdfTable, page);
t.addCsvToTable(data, DataTable.HASHEADER, ';');
pdfTable.draw();
```
Output : [CSVExamplePortrait.pdf](https://s3.amazonaws.com/misc.quodlibet.be/Boxable/CSVexamplePortrait.pdf)

## Create a pdf from a List

```java
List<List> data = new ArrayList();
data.add(new ArrayList<>(
               Arrays.asList("Column One", "Column Two", "Column Three", "Column Four", "Column Five")));
for (int i = 1; i <= 100; i++) {
  data.add(new ArrayList<>(
      Arrays.asList("Row " + i + " Col One", "Row " + i + " Col Two", "Row " + i + " Col Three", "Row " + i + " Col Four", "Row " + i + " Col Five")));
}
BaseTable dataTable = new BaseTable(yStart, yStartNewPage, bottomMargin, tableWidth, margin, doc, page, true, true);
DataTable t = new DataTable(dataTable, page);
t.addListToTable(data, DataTable.HASHEADER);
dataTable.draw();
```
Output : [ListExampleLandscape.pdf](https://s3.amazonaws.com/misc.quodlibet.be/Boxable/ListExampleLandscape.pdf)

## Build tables in pdf documents

```java
BaseTable table = new BaseTable(yStart, yStartNewPage, bottomMargin, tableWidth, margin, doc, page, true,
				drawContent);
//Create Header row
Row<PDPage> headerRow = table.createRow(15f);
Cell<PDPage> cell = headerRow.createCell(100, "Awesome Facts About Belgium");
cell.setFont(PDType1Font.HELVETICA_BOLD);
cell.setFillColor(Color.BLACK);
table.addHeaderRow(headerRow);
List<String[]> facts = getFacts();
for (String[] fact : facts) {
			Row<PDPage> row = table.createRow(10f);
			cell = row.createCell((100 / 3.0f) * 2, fact[0] );
			for (int i = 1; i < fact.length; i++) {
			   cell = row.createCell((100 / 9f), fact[i]);
			}
}
table.draw();
```

Special Thanks to these awesome contributers : 
- [@johnmanko](https://github.com/johnmanko)
- [@Vobarian](https://github.com/vobarian)
- [@Giboow](https://github.com/giboow)
- [@Ogmios-Voice](https://github.com/ogmios-voice)
- [@zaqpiotr](https://github.com/zaqpiotr)
- [Frulenzo](https://github.com/Frulenzo)
- [dgautier](https://github.com/dgautier)
- [ZeerDonker](https://github.com/ZeerDonker)
- [dobluth](https://github.com/dobluth)
- [schmitzhermes](https://github.com/schmitzhermes)

=======

Copyright [2022](Quodlibet.be)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
