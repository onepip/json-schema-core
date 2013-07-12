package com.github.fge.jsonschema.walk;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonschema.exceptions.ProcessingException;
import com.github.fge.jsonschema.keyword.SchemaDescriptor;
import com.github.fge.jsonschema.keyword.SchemaDescriptors;
import com.github.fge.jsonschema.messages.JsonSchemaCoreMessageBundle;
import com.github.fge.jsonschema.messages.JsonSchemaSyntaxMessageBundle;
import com.github.fge.jsonschema.report.ConsoleProcessingReport;
import com.github.fge.jsonschema.report.ProcessingMessage;
import com.github.fge.jsonschema.report.ProcessingReport;
import com.github.fge.jsonschema.syntax.checkers.SyntaxChecker;
import com.github.fge.jsonschema.tree.CanonicalSchemaTree;
import com.github.fge.jsonschema.tree.SchemaTree;
import com.github.fge.msgsimple.bundle.MessageBundle;
import com.github.fge.msgsimple.load.MessageBundles;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SchemaAnalyzer
    implements SchemaListener<Set<JsonPointer>>
{
    private static final MessageBundle CORE_BUNDLE
        = MessageBundles.getBundle(JsonSchemaCoreMessageBundle.class);


    private final Set<JsonPointer> visited = Sets.newHashSet();
    private final Set<String> supported;
    private final Map<String, SyntaxChecker> checkers;
    private final MessageBundle bundle;

    public SchemaAnalyzer(final SchemaDescriptor descriptor,
        final MessageBundle bundle)
    {
        supported = descriptor.getSupportedKeywords();
        checkers = Maps.newTreeMap();
        checkers.putAll(descriptor.getSyntaxCheckers());
        this.bundle = bundle;
    }

    @Override
    public void enteringPath(final JsonPointer path,
        final ProcessingReport report)
        throws ProcessingException
    {
        visited.add(path);
    }

    @Override
    public void visiting(final SchemaTree schemaTree,
        final ProcessingReport report)
        throws ProcessingException
    {
        final JsonNode node = schemaTree.getNode();

        if (!node.isObject()) {
            final ProcessingMessage message = new ProcessingMessage()
                .setMessage(CORE_BUNDLE.getMessage("core.notASchema"))
                .put("schema", schemaTree);
            report.error(message);
            return;
        }

        final Set<String> fields = Sets.newHashSet(node.fieldNames());
        final Sets.SetView<String> unknown = Sets.difference(fields, supported);

        if (!unknown.isEmpty()) {
            final ProcessingMessage message = new ProcessingMessage()
                .setMessage(CORE_BUNDLE.getMessage("core.unknownKeywords"))
                .putArgument("ignored", Ordering.natural().sortedCopy(unknown));
            report.warn(message);
        }

        final List<JsonPointer> list = Lists.newArrayList();
        for (final Map.Entry<String, SyntaxChecker> entry:
            checkers.entrySet()) {
            if (!fields.contains(entry.getKey()))
                continue;
            entry.getValue().checkSyntax(list, bundle, report, schemaTree);
        }
    }

    @Override
    public void exitingPath(final JsonPointer path,
        final ProcessingReport report)
        throws ProcessingException
    {
    }

    @Override
    public Set<JsonPointer> getValue()
    {
        return ImmutableSet.copyOf(visited);
    }

    public static void main(final String... args)
        throws IOException, ProcessingException
    {
        final MessageBundle bundle
            = MessageBundles.getBundle(JsonSchemaSyntaxMessageBundle.class);
        final SchemaDescriptor descriptor
            = SchemaDescriptors.draftv4();
        final ProcessingReport report = new ConsoleProcessingReport();
        final SchemaWalker walker = new DefaultSchemaWalker(descriptor);
        final SchemaListener<Set<JsonPointer>> listener
            = new SchemaAnalyzer(descriptor, bundle);
        final JsonNode node = JsonLoader.fromResource("/draftv3/schema");
        final SchemaTree tree = new CanonicalSchemaTree(node);
        walker.walk(tree, listener, report);
    }
}