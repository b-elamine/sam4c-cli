package sam4c.light.metamodel;

/**
 * Primitive field kinds an MAttribute can have.
 *
 *   STRING / BOOLEAN / INT  -- scalars (a STRING with allowed values is an enum)
 *   MAP                     -- a small string->string map (e.g. scale, resources, storage)
 *   LIST                    -- a list of strings (e.g. secrets)
 *
 * MAP/LIST keep structured fields describable in the metamodel without a metaclass each,
 * so the loader, conformance, Studio form, and serializer can all be metamodel-driven.
 */
public enum MDataType { STRING, BOOLEAN, INT, MAP, LIST }
