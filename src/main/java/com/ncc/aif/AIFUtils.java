package com.ncc.aif;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.*;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.XSD;

import java.util.*;

/**
 * A convenient interface for creating simple AIF graphs.
 * <p>
 * More complicated graphs will require direct manipulation of the RDF.
 *
 * @author Ryan Gabbard (USC ISI)
 * @author Converted to Java by Next Century Corporation
 */
public class AIFUtils {
    /**
     * Adds common non-ontology-specific namespaces to make AIF files more readable
     */
    public static void addStandardNamespaces(Model model) {
        model.setNsPrefix("rdf", RDF.uri);
        model.setNsPrefix("xsd", XSD.getURI());
        model.setNsPrefix("aida", AidaAnnotationOntology.NAMESPACE);
        model.setNsPrefix("skos", SKOS.uri);
    }

    /**
     * Create a resource representing the system which produced some data.
     * <p>
     * Such a resource should be attached to all entities, events, event arguments, relations,
     * sentiment assertions, confidences, justifications, etc. produced by a system. You should
     * only create the system resource once; reuse the returned objects for all calls
     * to [markSystem].
     *
     * @return The created system resource.
     */
    public static Resource makeSystemWithURI(Model model, String systemURI) {
        final Resource system = model.createResource(systemURI);
        system.addProperty(RDF.type, AidaAnnotationOntology.SYSTEM_CLASS);
        return system;
    }

    /**
     * Mark a resource as coming from the specified [system].
     */
    public static void markSystem(Resource toMarkOn, Resource system) {
        toMarkOn.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system);
    }

    /**
     * Mark [entity] as having the specified [name]
     */
    public static void markName(Resource entity, String name) {
        entity.addLiteral(AidaAnnotationOntology.NAME_PROPERTY, name);
    }

    /**
     * Mark [entity] as having the specified [textValue]
     */
    public static void markTextValue(Resource entity, String textValue) {
        entity.addLiteral(AidaAnnotationOntology.TEXT_VALUE_PROPERTY, textValue);
    }

    /**
     * Mark [entity] as having the specified [numericValue] as string
     */
    public static void markNumericValueAsString(Resource entity, String numericValue) {
        entity.addLiteral(AidaAnnotationOntology.NUMERIC_VALUE_PROPERTY, numericValue);
    }

    /**
     * Mark [entity] as having the specified [numericValue] as double floating point
     */
    public static void markNumericValueAsDouble(Resource entity, Number numericValue) {
        entity.addLiteral(AidaAnnotationOntology.NUMERIC_VALUE_PROPERTY, numericValue.doubleValue());
    }

    /**
     * Mark [entity] as having the specified [numericValue] as long integer
     */
    public static void markNumericValueAsLong(Resource entity, Number numericValue) {
        entity.addLiteral(AidaAnnotationOntology.NUMERIC_VALUE_PROPERTY, numericValue.longValue());
    }

    /**
     * Create an entity.
     *
     * @param model
     * @param entityUri can be any unique string.
     * @param system    The system object for the system which created this entity.
     */
    public static Resource makeEntity(Model model, String entityUri, Resource system) {
        return makeAIFResource(model, entityUri, AidaAnnotationOntology.ENTITY_CLASS, system);
    }

    /**
     * Create a relation
     *
     * @param relationUri can be any unique string.
     * @param system      The system object for the system which created this event.
     */
    public static Resource makeRelation(Model model, String relationUri, Resource system) {
        return makeAIFResource(model, relationUri, AidaAnnotationOntology.RELATION_CLASS, system);
    }

    /**
     * Makes a relation of type [relationType] between [subjectResource] and [objectResource] in a form
     * similar to that of an event: subjects and objects are explicitly linked to relation via [subjectRole]
     * and [objectRole], respectively.
     * <p>
     * If [confidence] is non-null the relation is marked with the given [confidence]
     *
     * @return The relation object
     */
    public static Resource makeRelationInEventForm(Model model, String relationUri, Resource relationType, Resource subjectRole,
                                                   Resource subjectResource, Resource objectRole, Resource objectResource,
                                                   String typeAssertionUri, Resource system, Double confidence) {
        final Resource relation = makeRelation(model, relationUri, system);
        markType(model, typeAssertionUri, relation, relationType, system, confidence);
        markAsArgument(model, relation, subjectRole, subjectResource, system, confidence);
        markAsArgument(model, relation, objectRole, objectResource, system, confidence);
        return relation;
    }

    /**
     * Create an event
     *
     * @param eventUri can be any unique string.
     * @param system   The system object for the system which created this event.
     */
    public static Resource makeEvent(Model model, String eventUri, Resource system) {
        return makeAIFResource(model, eventUri, AidaAnnotationOntology.EVENT_CLASS, system);
    }

    /**
     * Marks an entity as filling an argument role for an event or relation.
     *
     * @return The created event or relation argument assertion
     */
    public static Resource markAsArgument(Model model, Resource eventOrRelation, Resource argumentType,
                                          Resource argumentFiller, Resource system,
                                          Double confidence) {

        return markAsArgument(model, eventOrRelation, argumentType, argumentFiller, system, confidence, null);
    }

    /**
     * Marks an entity as filling an argument role for an event or relation.
     *
     * @return The created event or relation argument assertion with uri
     */
    public static Resource markAsArgument(Model model, Resource eventOrRelation, Resource argumentType,
                                          Resource argumentFiller, Resource system,
                                          Double confidence, String uri) {

        final Resource argAssertion = makeAIFResource(model, uri, RDF.Statement, system);

        argAssertion.addProperty(RDF.subject, eventOrRelation);
        argAssertion.addProperty(RDF.predicate, argumentType);
        argAssertion.addProperty(RDF.object, argumentFiller);
        if (confidence != null) {
            markConfidence(model, argAssertion, confidence, system);
        }
        return argAssertion;
    }

    /**
     * Mark an entity or event as having a specified type.
     * <p>
     * This is marked with a separate assertion so that uncertainty about type can be expressed.
     * In such a case, bundle together the type assertion resources returned by this method with
     * [markAsMutuallyExclusive].
     *
     * @param type   The type of the entity, event, or relation being asserted
     * @param system The system object for the system which created this entity.
     */
    public static Resource markType(Model model, String typeAssertionUri, Resource entityOrEventOrRelation,
                                    Resource type, Resource system, Double confidence) {
        final Resource typeAssertion = model.createResource(typeAssertionUri);
        typeAssertion.addProperty(RDF.type, RDF.Statement);
        typeAssertion.addProperty(RDF.subject, entityOrEventOrRelation);
        typeAssertion.addProperty(RDF.predicate, RDF.type);
        typeAssertion.addProperty(RDF.object, type);
        typeAssertion.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system);
        if (confidence != null) {
            markConfidence(model, typeAssertion, confidence, system);
        }
        return typeAssertion;
    }

    public static Resource makeAIFJustification(Model model, String docId, Resource classType,
                                                Resource system, Double confidence) {
        final Resource justification = makeAIFResource(model, null, classType, system);
        justification.addProperty(AidaAnnotationOntology.SOURCE, model.createTypedLiteral(docId));
        markConfidence(model, justification, confidence, system);
        return justification;
    }

    /**
     * Mark something as being justified by a particular justification
     */
    public static void markJustification(Resource toMarkOn, Resource justification) {
        toMarkOn.addProperty(AidaAnnotationOntology.JUSTIFIED_BY, justification);
    }

    /**
     * Mark multiple things as being justified by a particular justification
     */
    public static void markJustification(Collection<Resource> toMarkOn, Resource justification) {
        toMarkOn.forEach(it -> markJustification(it, justification));
    }

    /**
     * Create justification from a particular snippet of text.
     *
     * @return The text justification resource created.
     */
    public static Resource makeTextJustification(Model model, String docId, int startOffset, int endOffsetInclusive,
                                                 Resource system, Double confidence) {
        if (endOffsetInclusive < startOffset) {
            throw new IllegalArgumentException("End offset " + endOffsetInclusive + " precedes start offset " + startOffset);
        }
        if (startOffset < 0) {
            throw new IllegalArgumentException("Start offset must be non-negative but got " + startOffset);
        }

        final Resource justification = makeAIFJustification(model, docId, AidaAnnotationOntology.TEXT_JUSTIFICATION_CLASS,
                system, confidence);
        // the document ID for the justifying source document
        justification.addProperty(AidaAnnotationOntology.START_OFFSET,
                model.createTypedLiteral(startOffset));
        justification.addProperty(AidaAnnotationOntology.END_OFFSET_INCLUSIVE,
                model.createTypedLiteral(endOffsetInclusive));

        return justification;
    }

    /**
     * Mark something as being justified by a particular snippet of text.
     *
     * @return The text justification resource created.
     */
    public static Resource markTextJustification(Model model, Resource toMarkOn, String docId,
                                                 int startOffset, int endOffsetInclusive,
                                                 Resource system, Double confidence) {
        return markTextJustification(model, ImmutableSet.of(toMarkOn), docId, startOffset,
                endOffsetInclusive, system, confidence);
    }

    /**
     * Mark multiple things as being justified by a particular snippet of text.
     *
     * @return The text justification resource created.
     */
    public static Resource markTextJustification(Model model, Collection<Resource> toMarkOn, String docId,
                                                 int startOffset, int endOffsetInclusive,
                                                 Resource system, Double confidence) {
        final Resource justification = makeTextJustification(model, docId, startOffset, endOffsetInclusive, system, confidence);
        markJustification(toMarkOn, justification);
        return justification;
    }

    public static final class Point {
        private final int x;
        private final int y;

        public Point(int x, int y) {
            if (x < 0) {
                throw new IllegalArgumentException("Aida image/video coordinates must be non-negative but got " + x);
            }
            if (y < 0) {
                throw new IllegalArgumentException("Aida image/video coordinates must be non-negative but got " + y);
            }
            this.x = x;
            this.y = y;
        }

        public Point(Point point) {
            x = point.x;
            y = point.y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }

    public static final class BoundingBox {
        private final Point upperLeft;
        private final Point lowerRight;

        public BoundingBox(Point upperLeft, Point lowerRight) {
            if (upperLeft.x > lowerRight.x || upperLeft.y > lowerRight.y) {
                throw new IllegalArgumentException("Upper left of bounding box " + upperLeft +
                        " not above and to the left of lower right " + lowerRight);
            }
            this.upperLeft = new Point(upperLeft.x, upperLeft.y);
            this.lowerRight = new Point(lowerRight.x, lowerRight.y);
        }

        public BoundingBox(BoundingBox boundingBox) {
            upperLeft = new Point(boundingBox.upperLeft);
            lowerRight = new Point(boundingBox.lowerRight);
        }

        public Point getUpperLeft() {
            return upperLeft;
        }

        public Point getLowerRight() {
            return lowerRight;
        }

    }

    private static Resource markBoundingBox(Model model, Resource toMarkOn, BoundingBox boundingBox) {

        final Resource boundingBoxResource = model.createResource();
        boundingBoxResource.addProperty(RDF.type, AidaAnnotationOntology.BOUNDING_BOX_CLASS);
        boundingBoxResource.addProperty(AidaAnnotationOntology.BOUNDING_BOX_UPPER_LEFT_X,
                model.createTypedLiteral(boundingBox.upperLeft.x));
        boundingBoxResource.addProperty(AidaAnnotationOntology.BOUNDING_BOX_UPPER_LEFT_Y,
                model.createTypedLiteral(boundingBox.upperLeft.y));
        boundingBoxResource.addProperty(AidaAnnotationOntology.BOUNDING_BOX_LOWER_RIGHT_X,
                model.createTypedLiteral(boundingBox.lowerRight.x));
        boundingBoxResource.addProperty(AidaAnnotationOntology.BOUNDING_BOX_LOWER_RIGHT_Y,
                model.createTypedLiteral(boundingBox.lowerRight.y));

        toMarkOn.addProperty(AidaAnnotationOntology.BOUNDING_BOX_PROPERTY, boundingBoxResource);

        return boundingBoxResource;
    }

    public static Resource makeImageJustification(Model model, String docId, BoundingBox boundingBox, Resource system,
                                                  Double confidence) {
        final Resource justification = makeAIFJustification(model, docId, AidaAnnotationOntology.IMAGE_JUSTIFICATION_CLASS,
                system, confidence);
        markBoundingBox(model, justification, boundingBox);
        return justification;
    }

    public static Resource markImageJustification(Model model, Resource toMarkOn, String docId,
                                                  BoundingBox boundingBox, Resource system, Double confidence) {
        return markImageJustification(model, ImmutableSet.of(toMarkOn), docId, boundingBox, system, confidence);
    }

    public static Resource markImageJustification(Model model, Collection<Resource> toMarkOn, String docId,
                                                  BoundingBox boundingBox, Resource system, Double confidence) {
        final Resource justification = makeImageJustification(model, docId, boundingBox, system, confidence);
        markJustification(toMarkOn, justification);
        return justification;
    }

    /**
     * Create a justification from something appearing in a key frame of a video.
     */
    public static Resource makeKeyFrameVideoJustification(Model model, String docId, String keyFrame, BoundingBox boundingBox,
                                                          Resource system, Double confidence) {
        final Resource justification = makeAIFJustification(model, docId, AidaAnnotationOntology.KEYFRAME_VIDEO_JUSTIFICATION_CLASS,
                system, confidence);
        justification.addProperty(AidaAnnotationOntology.KEY_FRAME, model.createTypedLiteral(keyFrame));
        markBoundingBox(model, justification, boundingBox);
        return justification;
    }

    /**
     * Marks a justification for something appearing in a key frame of a video.
     */
    public static Resource markKeyFrameVideoJustification(Model model, Resource toMarkOn, String docId, String keyFrame,
                                                          BoundingBox boundingBox, Resource system, Double confidence) {
        return markKeyFrameVideoJustification(model, ImmutableSet.of(toMarkOn), docId,
                keyFrame, boundingBox, system, confidence);
    }

    /**
     * Marks a justification for something appearing in a key frame of a video.
     */
    public static Resource markKeyFrameVideoJustification(Model model, Collection<Resource> toMarkOn, String docId, String keyFrame,
                                                          BoundingBox boundingBox, Resource system, Double confidence) {
        final Resource justification = makeKeyFrameVideoJustification(model, docId, keyFrame, boundingBox, system, confidence);
        markJustification(toMarkOn, justification);
        return justification;
    }

    /**
     * Create a justification from something appearing in a video but not in a key frame.
     */
    public static Resource makeShotVideoJustification(Model model, String docId, String shotId, Resource system,
                                                      Double confidence) {
        final Resource justification = makeAIFJustification(model, docId, AidaAnnotationOntology.SHOT_VIDEO_JUSTIFICATION_CLASS,
                system, confidence);
        justification.addProperty(AidaAnnotationOntology.SHOT, model.createTypedLiteral(shotId));
        return justification;
    }

    /**
     * Marks a justification for something appearing in a video but not in a key frame.
     */
    public static Resource markShotVideoJustification(Model model, Resource toMarkOn, String docId, String shotId,
                                                      Resource system, Double confidence) {
        return markShotVideoJustification(model, ImmutableSet.of(toMarkOn), docId, shotId, system, confidence);
    }

    /**
     * Marks a justification for something appearing in a video but not in a key frame.
     */
    public static Resource markShotVideoJustification(Model model, Collection<Resource> toMarkOn, String docId, String shotId,
                                                      Resource system, Double confidence) {
        final Resource justification = makeShotVideoJustification(model, docId, shotId, system, confidence);
        markJustification(toMarkOn, justification);
        return justification;
    }

    public static Resource makeAudioJustification(Model model, String docId, Double startTimestamp, Double endTimestamp,
                                                  Resource system, Double confidence) {
        if (endTimestamp <= startTimestamp) {
            throw new IllegalArgumentException("End timestamp " + endTimestamp
                    + "does not follow start timestamp " + startTimestamp);
        }
        final Resource justification = makeAIFJustification(model, docId, AidaAnnotationOntology.AUDIO_JUSTIFICATION_CLASS,
                system, confidence);

        justification.addProperty(AidaAnnotationOntology.START_TIMESTAMP,
                model.createTypedLiteral(startTimestamp));
        justification.addProperty(AidaAnnotationOntology.END_TIMESTAMP,
                model.createTypedLiteral(endTimestamp));

        return justification;
    }

    public static Resource markAudioJustification(Model model, Resource toMarkOn, String docId,
                                                  Double startTimestamp, Double endTimestamp,
                                                  Resource system, Double confidence) {
        return markAudioJustification(model, ImmutableSet.of(toMarkOn), docId,
                startTimestamp, endTimestamp, system, confidence);
    }

    public static Resource markAudioJustification(Model model, Collection<Resource> toMarkOn, String docId,
                                                  Double startTimestamp, Double endTimestamp,
                                                  Resource system, Double confidence) {
        final Resource justification = makeAudioJustification(model, docId, startTimestamp, endTimestamp, system, confidence);
        markJustification(toMarkOn, justification);
        return justification;
    }

    public static Resource markCompoundJustification(Model model, Collection<Resource> toMarkOn,
                                                     Collection<Resource> justifications,
                                                     Resource system, Double confidence) {
        final Resource compoundJustification = makeAIFResource(model, null,
                AidaAnnotationOntology.COMPOUND_JUSTIFICATION_CLASS, system);
        markConfidence(model, compoundJustification, confidence, system);
        justifications.forEach(j -> compoundJustification.addProperty(AidaAnnotationOntology.CONTAINED_JUSTIFICATION, j));
        markJustification(toMarkOn, compoundJustification);
        return compoundJustification;
    }

    /**
     * Mark a confidence value on a resource.
     */
    public static void markConfidence(Model model, Resource toMarkOn, Double confidence, Resource system) {
        Resource confidenceBlankNode = model.createResource();
        confidenceBlankNode.addProperty(RDF.type, AidaAnnotationOntology.CONFIDENCE_CLASS);
        confidenceBlankNode.addProperty(AidaAnnotationOntology.CONFIDENCE_VALUE, model.createTypedLiteral(confidence));
        markSystem(confidenceBlankNode, system);
        toMarkOn.addProperty(AidaAnnotationOntology.CONFIDENCE, confidenceBlankNode);
    }

    /**
     * Mark the given resources as mutually exclusive.
     * <p>
     * This is a special case of [markAsMutuallyExclusive] where the alternatives are each single edges,
     * so we simply wrap each edge in a collection and pass to markAsMutuallyExclusive.
     *
     * @param alternatives       is a map from alternate edges to the confidence associated with each alternative.
     * @param noneOfTheAboveProb - if non-null, the given confidence will be applied to the
     *                           "none of the above" option.
     * @return The mutual exclusion assertion.
     */
    public static Resource markEdgesAsMutuallyExclusive(Model model, ImmutableMap<Resource, Double> alternatives,
                                                        Resource system, Double noneOfTheAboveProb) {

        HashMap<Collection<Resource>, Double> newAltMap = new HashMap<>();
        alternatives.keySet().forEach(edge ->
                newAltMap.put(ImmutableSet.of(edge), alternatives.get(edge)));
        return markAsMutuallyExclusive(model, newAltMap, system, noneOfTheAboveProb);
    }

    /**
     * @param alternatives       is a map from the collection of edges which form a sub-graph for
     *                           an alternative to the confidence associated with an alternative.
     * @param noneOfTheAboveProb - if non-null, the given confidence will be applied to the
     *                           "none of the above" option.
     * @return The mutual exclusion assertion.
     */
    public static Resource markAsMutuallyExclusive(Model model, Map<Collection<Resource>, Double> alternatives,
                                                   Resource system, Double noneOfTheAboveProb) {
        if (alternatives.size() < 2) {
            throw new IllegalArgumentException("Must have at least two mutually exclusive " +
                    "things when making a mutual exclusion constraint, but got " + alternatives.size());
        }
        final Resource mutualExclusionAssertion =
                makeAIFResource(model, null, AidaAnnotationOntology.MUTUAL_EXCLUSION_CLASS, system);

        // Iterate through each subgraph (collection of edges)
        for (Collection<Resource> edges : alternatives.keySet()) {
            final Resource alternative = model.createResource();
            alternative.addProperty(RDF.type, AidaAnnotationOntology.MUTUAL_EXCLUSION_ALTERNATIVE_CLASS);
            final Resource alternativeGraph = model.createResource();
            alternativeGraph.addProperty(RDF.type, AidaAnnotationOntology.SUBGRAPH_CLASS);

            for (Resource edge : edges) {
                alternativeGraph.addProperty(AidaAnnotationOntology.GRAPH_CONTAINS, edge);
            }

            alternative.addProperty(AidaAnnotationOntology.ALTERNATIVE_GRAPH_PROPERTY, alternativeGraph);
            markConfidence(model, alternative, alternatives.get(edges), system);
            mutualExclusionAssertion.addProperty(AidaAnnotationOntology.ALTERNATIVE_PROPERTY, alternative);
        }

        if (noneOfTheAboveProb != null) {
            mutualExclusionAssertion.addProperty(AidaAnnotationOntology.NONE_OF_THE_ABOVE_PROPERTY,
                    model.createTypedLiteral(noneOfTheAboveProb));
        }

        return mutualExclusionAssertion;
    }

    /**
     * Create a "same-as" cluster.
     * <p>
     * A same-as cluster is used to represent multiple entities which might be the same, but we
     * aren't sure. (If we were sure, they would just be a single node).
     * <p>
     * Every cluster requires a [prototype] - an entity or event that we are *certain* is in the
     * cluster. This also automatically adds a membership relation with the prototype with confidence 1.0.
     *
     * @return The cluster created
     */
    public static Resource makeClusterWithPrototype(Model model, String clusterUri, Resource prototype,
                                                    Resource system) {
        final Resource cluster = makeAIFResource(model, clusterUri, AidaAnnotationOntology.SAME_AS_CLUSTER_CLASS, system);
        cluster.addProperty(AidaAnnotationOntology.PROTOTYPE, prototype);
        markAsPossibleClusterMember(model, prototype, cluster, 1.0, system);
        return cluster;
    }

    /**
     * Mark an entity or event as a possible member of a cluster.
     *
     * @return The cluster membership assertion
     */
    public static Resource markAsPossibleClusterMember(Model model, Resource possibleClusterMember,
                                                       Resource cluster, Double confidence,
                                                       Resource system) {
        final Resource clusterMemberAssertion = makeAIFResource(model, null,
                AidaAnnotationOntology.CLUSTER_MEMBERSHIP_CLASS, system);
        clusterMemberAssertion.addProperty(AidaAnnotationOntology.CLUSTER_PROPERTY, cluster);
        clusterMemberAssertion.addProperty(AidaAnnotationOntology.CLUSTER_MEMBER, possibleClusterMember);
        markConfidence(model, clusterMemberAssertion, confidence, system);
        return clusterMemberAssertion;
    }

    /**
     * Create a hypothesis
     * <p>
     * You can then indicate that some other object depends on this hypothesis using
     * [markDependsOnHypothesis].
     *
     * @return The hypothesis resource.
     */
    public static Resource makeHypothesis(Model model, String hypothesisURI, Set<Resource> hypothesisContent,
                                          Double confidence, Resource system) {
        if (hypothesisContent.isEmpty()) {
            throw new IllegalArgumentException("A hypothesis must have content");
        }
        final Resource hypothesis = makeAIFResource(model, hypothesisURI, AidaAnnotationOntology.HYPOTHESIS_CLASS, system);
        final Resource subgraph = model.createResource();
        subgraph.addProperty(RDF.type, AidaAnnotationOntology.SUBGRAPH_CLASS);

        for (Resource h : hypothesisContent) {
            subgraph.addProperty(AidaAnnotationOntology.GRAPH_CONTAINS, h);
        }

        hypothesis.addProperty(AidaAnnotationOntology.HYPOTHESIS_CONTENT_PROPERTY, subgraph);

        if (confidence != null) {
            markConfidence(model, hypothesis, confidence, system);
        }

        return hypothesis;
    }

    /**
     * Create a hypothesis
     * <p>
     * You can then indicate that some other object depends on this hypothesis using
     * [markDependsOnHypothesis].
     *
     * @return The hypothesis resource.
     */
    public static Resource makeHypothesis(Model model, String hypothesisURI, Set<Resource> hypothesisContent,
                                          Resource system) {
        return makeHypothesis(model, hypothesisURI, hypothesisContent, null, system);
    }

    public static void markDependsOnHypothesis(Resource depender, Resource hypothesis) {
        depender.addProperty(AidaAnnotationOntology.DEPENDS_ON_HYPOTHESIS, hypothesis);
    }

    public static Resource markPrivateData(Model model, Resource resource, String jsonContent,
                                           Resource system) {
        final Resource privateData = makeAIFResource(model, null, AidaAnnotationOntology.PRIVATE_DATA_CLASS, system);
        privateData.addProperty(AidaAnnotationOntology.JSON_CONTENT_PROPERTY, model.createTypedLiteral(jsonContent));

        resource.addProperty(AidaAnnotationOntology.PRIVATE_DATA_PROPERTY, privateData);

        return privateData;
    }

    /**
     * Private data should not contain document-level content features. Allowable private data include:
     * <p>
     * fringe type(s) for the KE
     * a vectorized representation of the KE, which cannot grow as the number of mentions/justifications for the KE
     * increases, and from which a raw document (or significant portions thereof) cannot be recoverable.
     * The number of documents that justify the KE time stamps of justification documents fringe type(s) for each
     * image or shot, to describe features that are not represented explicitly in the seedling ontology
     * For example: Physical.LocatedNear.Inside(Arg1_Type=Person.Soldier, Arg2_Type=Facility.Hospital)
     * <p>
     * The KE is not allowed to contain any strings from document text except for the strings in the HasName,
     * NumericValue, and TextValue properties.
     */
    public static Resource markPrivateData(Model model, Resource resource, String vectorType,
                                           List<Double> vectorData, Resource system) throws JsonProcessingException {
        final ObjectMapper mapper = new ObjectMapper();
        final ImmutableMap<String, Object> jsonMap =
                ImmutableMap.of("vector_type", vectorType, "vector_data", vectorData);
        return markPrivateData(model, resource, mapper.writeValueAsString(jsonMap), system);
    }

    public static Resource linkToExternalKB(Model model, Resource toLink, String externalKbId, Resource system,
                                            Double confidence) {
        final Resource linkAssertion = makeAIFResource(model, null, AidaAnnotationOntology.LINK_ASSERTION_CLASS, system);
        toLink.addProperty(AidaAnnotationOntology.LINK, linkAssertion);
        linkAssertion.addProperty(AidaAnnotationOntology.LINK_TARGET, model.createTypedLiteral(externalKbId));
        if (confidence != null) {
            markConfidence(model, linkAssertion, confidence, system);
        }
        return linkAssertion;
    }


    static final class SparqlQueries {
        static final Query TYPE_QUERY = QueryFactory.create(
                ("PREFIX rdf: <" + RDF.uri + ">\n" +
                        "SELECT ?typeAssertion WHERE {\n" +
                        "?typeAssertion a rdf:Statement .\n" +
                        "?typeAssertion rdf:predicate rdf:type .\n" +
                        "?typeAssertion rdf:subject ?typedObject .\n" +
                        "}").replace("\n", System.getProperty("line.separator")));
    }

    public static ImmutableSet<Resource> getTypeAssertions(Model model, Resource typedObject)  {
        final QuerySolutionMap boundVariables = new QuerySolutionMap();
        boundVariables.add("typedObject", typedObject);

        final QueryExecution queryExecution =
                QueryExecutionFactory.create(SparqlQueries.TYPE_QUERY, model, boundVariables);
        final ResultSet results = queryExecution.execSelect();

        HashSet<Resource> matchSet = new HashSet<>();
        while (results.hasNext()) {
            final QuerySolution match = results.nextSolution();
            matchSet.add(match.get("typeAssertion").asResource()); // check for null?
        }

        return ImmutableSet.<Resource>builder()
                .addAll(matchSet)
                .build();
    }

    public static ImmutableSet<Resource> getConfidenceAssertions(Model model, Resource confidencedObject) {
        NodeIterator iter =
                model.listObjectsOfProperty(confidencedObject, AidaAnnotationOntology.CONFIDENCE);
        HashSet<Resource> matchSet = new HashSet<>();
        while (iter.hasNext()) {
            matchSet.add(iter.nextNode().asResource());
        }

        return ImmutableSet.<Resource>builder()
                .addAll(matchSet)
                .build();
    }

    private static Resource makeAIFResource(Model model, String uri, Resource classType, Resource system) {
        Resource resource = (uri == null ? model.createResource() : model.createResource(uri));
        resource.addProperty(RDF.type, classType);
        markSystem(resource, system);
        return resource;
    }
}

/**
 * A strategy for generating RDF graph nodes
 */
interface IriGenerator {
    String nextIri();
}

/**
 * A node generation strategy which uses UUIDs appended to a base URI.
 */
final class UuidIriGenerator implements IriGenerator {
    private String baseUri;

    UuidIriGenerator() {
        baseUri = "dummy:uri";
    }

    UuidIriGenerator(String baseUri) {
        if (baseUri == null || baseUri.isEmpty()) {
            throw new IllegalArgumentException("Base URI cannot be empty");
        }
        if (!baseUri.substring(1).contains(":")) {
            throw new IllegalArgumentException("Base URI must contain a prefix followed by a colon separator");
        }
        if (baseUri.endsWith("/")) {
            throw new IllegalArgumentException("Base URI cannot end in /");
        }
        this.baseUri = baseUri;
    }

    @Override
    public String nextIri() {
        return baseUri + '/' + UUID.randomUUID().toString();
    }
}
