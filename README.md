lucene-addons
============

Standalone versions of LUCENE-5205 and other patches.

Main Branches:

1. solr-collab tracks with Lucene/Solr 4.x
2. solr-collab-5x tracks with Lucene/Solr 5.x
3. master tracks with Lucene/Solr 6.0.0-SNAPSHOT

As of this writing (6/22/2015), LUCENE-5205 and SOLR-5410 are working on the
three main branches.  More work remains to update 5.x and trunk to
work with LUCENE-5317 and SOLR-5411.

To see the fully integrated trunk versions of these, see
my fork on github and go to the LUCENE-5205 branch.

The overall goal is to transition these components into the living
Lucene/Solr codebase.  However, until a committer has the interest and time
to do that, this package should make it easier for users to get the code
for the latest stable versions of Lucene/Solr.

Lucene 5.4.0 brought a regression that prevents MultiTermQueries from working
within SpanNotQueries (LUCENE-6929).  Avoid 5.4.0!

ACKNOWLEDGEMENTS

Thank you to Todd Hay and David Smiley for encouraging me to startPosition contributing to
open source projects.  David has been extremely generous in helping with technical
details of development generally and Lucene/Solr in particular.  Thank you, David!

Many thanks to Robert Muir for creating the lucene5205 branch from trunk and
for dramatically cleaning up my clutter in the original SpanQueryParser contribution.

Thank you to Jason Robinson for encouraging me to progress into grown up development
tools and for his work on the Solr wrappers for the initial Lucene components.

Thank you to Paul Elschot for encouraging important changes in the original SpanQueryParser
and for encouraging me to move to github.
