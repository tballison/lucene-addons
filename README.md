lucene-addons
======================

Standalone versions of LUCENE-5205 and other patches.

Main Branches:
The branches are named for the version of Lucene/Solr then the branch id, e.g.
lucene5.5-0.2 is the more recent working branch tagged to Lucene 5.5.x.

As of this writing (5/23/2016), LUCENE-5205, SOLR-5410 and LUCENE-5317 are 
working on all branches.  Still need to update/refactor SOLR-5411.

The overall goal is to transition these components into the living
Lucene/Solr codebase.  However, until a committer has the interest and time
to do that, this package should make it easier for users to get the code
for the latest stable versions of Lucene/Solr.

Lucene 5.4.0 brought a regression that prevents MultiTermQueries from working
within SpanNotQueries (LUCENE-6929).  Avoid 5.4.0!


ACKNOWLEDGEMENTS

Thank you to Todd Hay and David Smiley for encouraging me to start contributing to
open source projects.  David has been extremely generous in helping with technical
details of development generally and Lucene/Solr in particular.  Thank you, David!

Many thanks to Robert Muir for creating the lucene5205 branch from trunk and
for dramatically cleaning up my clutter in the original SpanQueryParser contribution.

Thank you to Jason Robinson for encouraging me to progress into grown up development
tools and for his work on the Solr wrappers for the initial Lucene components.

Thank you to Paul Elschot for encouraging important changes in the original SpanQueryParser
and for encouraging me to move to github.

Thank you to Luke Nezda for collaboration on Issue #7 (add SpanPositionRangeQuery).