package ru.tinkoff.kora.http.server.common.router;


import java.util.*;

/**
 * Represents a parsed web socket path template.
 * <p>
 * This class can be compared to other path templates, with templates that are considered
 * lower have a higher priority, and should be checked first.
 * <p>
 * This comparison can also be used to check for semantically equal paths, if
 * a.compareTo(b) == 0 then the two paths are equivalent, which will generally
 * result in a deployment exception.
 *
 * @author Stuart Douglas
 */
public record PathTemplate(String templateString, boolean template, String base, List<Part> parts, Set<String> parameterNames, boolean trailingSlash) implements Comparable<PathTemplate> {

    public PathTemplate {
        parameterNames = Collections.unmodifiableSet(parameterNames);
    }

    public static PathTemplate create(final String inputPath) {
        // a path is required
        if (inputPath == null) {
            throw new IllegalArgumentException("Path must be specified");
        }

        // prepend a "/" if none is present
        if (!inputPath.startsWith("/")) {
            return PathTemplate.create("/" + inputPath);
        }

        // create string from modified string
        final String path = inputPath;

        int state = 0;
        String base = "";
        List<Part> parts = new ArrayList<>();
        int stringStart = 0;
        //0 parsing base
        //1 parsing base, last char was /
        //2 in template part
        //3 just after template part, expecting /
        //4 expecting either template or segment
        //5 in segment

        for (int i = 0; i < path.length(); ++i) {
            final int c = path.charAt(i);
            switch (state) {
                case 0: {
                    if (c == '/') {
                        state = 1;
                    } else if (c == '*') {
                        base = path.substring(0, i + 1);
                        stringStart = i;
                        state = 5;
                    } else {
                        state = 0;
                    }
                    break;
                }
                case 1: {
                    if (c == '{') {
                        base = path.substring(0, i);
                        stringStart = i + 1;
                        state = 2;
                    } else if (c == '*') {
                        base = path.substring(0, i + 1);
                        stringStart = i;
                        state = 5;
                    } else if (c != '/') {
                        state = 0;
                    }
                    break;
                }
                case 2: {
                    if (c == '}') {
                        Part part = new Part(true, path.substring(stringStart, i));
                        parts.add(part);
                        stringStart = i;
                        state = 3;
                    }
                    break;
                }
                case 3: {
                    if (c == '/') {
                        state = 4;
                    } else {
                        throw new IllegalArgumentException("Could not parse URI template %s, exception at char %s".formatted(path, path.length()));
                    }
                    break;
                }
                case 4: {
                    if (c == '{') {
                        stringStart = i + 1;
                        state = 2;
                    } else if (c != '/') {
                        stringStart = i;
                        state = 5;
                    }
                    break;
                }
                case 5: {
                    if (c == '/') {
                        Part part = new Part(false, path.substring(stringStart, i));
                        parts.add(part);
                        stringStart = i + 1;
                        state = 4;
                    }
                    break;
                }
            }
        }
        boolean trailingSlash = false;
        switch (state) {
            case 1:
                trailingSlash = true;
                //fall through
            case 0: {
                base = path;
                break;
            }
            case 2: {
                throw new IllegalArgumentException("Could not parse URI template %s, exception at char %s".formatted(path, path.length()));
            }
            case 4: {
                trailingSlash = true;
                break;
            }
            case 5: {
                Part part = new Part(false, path.substring(stringStart));
                parts.add(part);
                break;
            }
        }
        final Set<String> templates = new HashSet<>();
        for (Part part : parts) {
            if (part.template) {
                templates.add(part.part);
            }
        }
        return new PathTemplate(path, state > 1 && !base.contains("*"), base, parts, templates, trailingSlash);
    }

    public boolean matches(final String path, final Map<String, String> pathParameters) {
        return matches(path, pathParameters, false);
    }

    /**
     * Check if the given url matches the template. If so then it will return true and
     * place the value of any path parameters into the given map.
     * <p>
     * Note the map may be modified even if the match in unsuccessful, however in this case
     * it will be emptied before the method returns
     *
     * @param path           The request path, relative to the context root
     * @param pathParameters The path parameters map to fill out
     * @return true if the URI is a match
     */
    public boolean matches(final String path, final Map<String, String> pathParameters, boolean ignoreTrailingSlash) {
        if (!template && base.contains("*")) {
            final int indexOf = base.indexOf("*");
            final String startBase = base.substring(0, indexOf);
            if (!path.startsWith(startBase)) {
                return false;
            }
            pathParameters.put("*", path.substring(indexOf));
            return true;
        }

        if (ignoreTrailingSlash && !template) {
            if (base.length() == path.length()) {
                return base.equals(path);
            } else if (base.length() - 1 == path.length()
                       && base.startsWith(path)
                       && base.endsWith("/")) {
                return true;
            } else if (base.length() == path.length() - 1
                       && path.startsWith(base)
                       && path.endsWith("/")) {
                return true;
            } else {
                return false;
            }
        } else {
            if (!path.startsWith(base)) {
                return false;
            }

            if (!template) {
                return path.length() == base.length();
            }

            if(!ignoreTrailingSlash) {
                if (trailingSlash) {
                    //the template has a trailing slash
                    //we verify this first as it is cheap
                    //and it simplifies the matching algorithm below
                    if (path.charAt(path.length() - 1) != '/') {
                        return false;
                    }
                } else if (path.charAt(path.length() - 1) == '/') {
                    return false;
                }
            }
        }

        int currentPartPosition = 0;
        PathTemplate.Part current = parts.get(currentPartPosition);
        int stringStart = base.length();
        int i;
        for (i = base.length(); i < path.length(); ++i) {
            final char currentChar = path.charAt(i);
            if (currentChar == '?' || current.part.equals("*")) {
                break;
            } else if (currentChar == '/') {
                String result = path.substring(stringStart, i);
                if (current.template) {
                    pathParameters.put(current.part, result);
                } else if (!result.equals(current.part)) {
                    pathParameters.clear();
                    return false;
                }
                ++currentPartPosition;
                if (currentPartPosition == parts.size()) {
                    //this is a match if this is the last character
                    return i == (path.length() - 1);
                }
                current = parts.get(currentPartPosition);
                stringStart = i + 1;
            }
        }
        if (currentPartPosition + 1 != parts.size()) {
            pathParameters.clear();
            return false;
        }

        String result = path.substring(stringStart, i);
        if (current.part.equals("*")) {
            pathParameters.put(current.part, path.substring(stringStart));
            return true;
        }
        if (current.template) {
            pathParameters.put(current.part, result);
        } else if (!result.equals(current.part)) {
            pathParameters.clear();
            return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof PathTemplate that) {
            return this.compareTo(that) == 0;
        } else {
            return false;
        }
    }

    @Override
    public int compareTo(final PathTemplate o) {
        //we want templates with the highest priority to sort first
        //so we sort in reverse priority order

        //templates have lower priority
        if (template && !o.template) {
            return 1;
        } else if (o.template && !template) {
            return -1;
        }

        int res = base.compareTo(o.base);
        if (res > 0) {
            //our base is longer
            return -1;
        } else if (res < 0) {
            return 1;
        } else if (!template) {
            //they are the same path
            return 0;
        }

        //the first path with a non-template element
        int i = 0;
        for (; ; ) {
            if (parts.size() == i) {
                if (o.parts.size() == i) {
                    return Boolean.compare(trailingSlash, o.trailingSlash);
                }
                return 1;
            } else if (o.parts.size() == i) {
                //we have more parts, so should be checked first
                return -1;
            }
            Part thisPath = parts.get(i);
            Part otherPart = o.parts.get(i);
            if (thisPath.template && !otherPart.template) {
                //non template part sorts first
                return 1;
            } else if (!thisPath.template && otherPart.template) {
                return -1;
            } else if (!thisPath.template) {
                int r = thisPath.part.compareTo(otherPart.part);
                if (r != 0) {
                    return r;
                }
            }
            ++i;
        }
    }

    private record Part(boolean template, String part) {}
}
