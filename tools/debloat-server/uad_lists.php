<?php
// Builds package-keyed JSON from MuntashirAkon's five Android Debloat List categories.
// The default browser build uses only these upstream records and replacements.
// Optional --supplement / --uadng flags remain for historical audit tooling. --overlay builds a
// device-reviewed list (the Dockerfile serves one per file under /devices/<name>/uad_lists.json). The removal mapping preserves Canta-compatible badges.
// Usage: php uad_lists.php <adl root> <output file> [--uadng <file>] [--supplement <file>] [--overlay <file>]...
declare(strict_types=1);

const LISTS = ['aosp' => 'Aosp', 'carrier' => 'Carrier', 'google' => 'Google', 'misc' => 'Misc', 'oem' => 'Oem'];
const REMOVALS = ['delete' => 'Recommended', 'replace' => 'Advanced', 'caution' => 'Expert', 'unsafe' => 'Unsafe'];
const SOURCE_ADL = 'Android Debloat List (MuntashirAkon)';
const SOURCE_UADNG = 'UAD-NG (Universal Debloater Alliance)';

function fail(string $message): never { fwrite(STDERR, "uad_lists.php: $message\n"); exit(1); }
function note(string $message): void { fwrite(STDERR, "uad_lists.php: $message\n"); }
function read(string $file): array { return json_decode(file_get_contents($file), true, 512, JSON_THROW_ON_ERROR); }

$args = array_slice($argv, 1);
$root = array_shift($args) ?? fail('missing <adl root>');
$out = array_shift($args) ?? fail('missing <output file>');
$uadng = null;
$supplements = [];
$overlays = [];
while ($args) {
    $flag = array_shift($args);
    $value = array_shift($args) ?? fail("$flag needs a file");
    match ($flag) {
        '--uadng' => $uadng = $value,
        '--supplement' => $supplements[] = $value,
        '--overlay' => $overlays[] = $value,
        default => fail("unknown option $flag"),
    };
}

// Legacy clients use display names; Cana also receives the complete replacement records.
$suggestionNames = function (string $id) use ($root): string {
    $file = "$root/suggestions/$id.json";
    return is_file($file) ? implode(', ', array_column(read($file), 'label')) : '';
};

/** Converts one record in ADL's schema into UAD-NG's shape. */
function convert(array $item, string $list, string $source, callable $suggestionNames, bool $appendWeb, string $root): array {
    $description = trim($item['description']);
    if (!empty($item['warning'])) $description = trim("$description\n\nWarning: " . trim($item['warning']));
    if ($appendWeb && !empty($item['web'])) $description .= "\n\n" . implode("\n", $item['web']);
    $entry = [
        'list' => $list,
        'description' => $description,
        'dependencies' => $item['dependencies'] ?? [],
        'neededBy' => $item['required_by'] ?? [],
        'labels' => $item['tags'] ?? [],
        'removal' => REMOVALS[$item['removal']] ?? throw new RuntimeException("Unknown removal for {$item['id']}"),
        'source' => $source,
    ];
    foreach (['label', 'warning', 'web'] as $field) {
        if (isset($item[$field])) $entry[$field] = $item[$field];
    }
    if (!empty($item['suggestions'])) {
        $names = $suggestionNames($item['suggestions']);
        if ($names !== '') $entry['suggestions'] = $names;
        $file = "$root/suggestions/{$item['suggestions']}.json";
        if (is_file($file)) $entry['alternatives'] = read($file);
    }
    return $entry;
}

$uad = [];
$counts = [];
foreach (LISTS as $file => $list) {
    foreach (read("$root/$file.json") as $item) {
        $uad[$item['id']] = convert($item, $list, SOURCE_ADL, $suggestionNames, false, $root);
    }
}
$counts[SOURCE_ADL] = count($uad);

foreach ($supplements as $file) {
    $source = basename($file) . ' (debloat server supplement)';
    $items = read($file);
    if (!array_is_list($items)) fail("$file must be a JSON array in ADL's schema");
    $seen = [];
    $added = 0;
    foreach ($items as $index => $item) {
        $id = $item['id'] ?? fail("$file: entry $index has no id");
        if (!preg_match('/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/', $id)) fail("$file: '$id' is not a package name");
        if (isset($seen[$id])) fail("$file lists $id twice");
        $seen[$id] = true;
        if (empty($item['description']) || !isset(REMOVALS[$item['removal'] ?? ''])) {
            fail("$file: $id needs a description and a removal of delete, replace, caution or unsafe");
        }
        if (!empty($item['suggestions']) && !is_file("$root/suggestions/{$item['suggestions']}.json")) {
            fail("$file: $id refers to unknown suggestions '{$item['suggestions']}'");
        }
        if (isset($uad[$id])) { note("$id from $file is already in ADL; keeping ADL's entry"); continue; }
        $uad[$id] = convert($item, 'Misc', $source, $suggestionNames, true, $root);
        $added++;
    }
    $counts[$source] = $added;
}

if ($uadng !== null) {
    $added = 0;
    $skipped = 0;
    foreach (read($uadng) as $id => $item) {
        if (isset($uad[$id])) continue;
        // UAD-NG's data has a few typo'd keys; copy only the known ones and skip entries with unknown enums.
        if (!in_array($item['list'] ?? null, LISTS, true) || !in_array($item['removal'] ?? null, REMOVALS, true)) { $skipped++; continue; }
        $entry = [
            'list' => $item['list'],
            'description' => trim((string) ($item['description'] ?? '')),
            'dependencies' => array_values(array_filter($item['dependencies'] ?? [], 'is_string')),
            'neededBy' => array_values(array_filter($item['neededBy'] ?? [], 'is_string')),
            'labels' => array_values(array_filter($item['labels'] ?? [], 'is_string')),
            'removal' => $item['removal'],
            'source' => SOURCE_UADNG,
        ];
        if (!empty($item['suggestions']) && is_string($item['suggestions'])) {
            $entry['suggestions'] = $suggestionNames($item['suggestions']) ?: $item['suggestions'];
        }
        $uad[$id] = $entry;
        $added++;
    }
    if ($skipped) note("skipped $skipped UAD-NG entries with unknown list or removal values");
    $counts[SOURCE_UADNG] = $added;
}

foreach ($overlays as $file) {
    $items = read($file);
    if (!$items || array_is_list($items)) fail("$file must be a nonempty package-keyed UAD object");
    $replaced = 0;
    $added = 0;
    foreach ($items as $id => $item) {
        if (!is_string($id) || !preg_match('/^(?:android|[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+)$/', $id)) fail("$file: invalid package ID");
        if (!is_array($item) || !in_array($item['list'] ?? null, LISTS, true)
            || !in_array($item['removal'] ?? null, REMOVALS, true)) fail("$file: $id has an unknown list/removal");
        foreach (['description', 'source'] as $field) {
            if (!is_string($item[$field] ?? null) || trim($item[$field]) === '') fail("$file: $id needs a nonempty $field");
        }
        foreach (['dependencies', 'neededBy', 'labels'] as $field) {
            $values = $item[$field] ?? null;
            if (!is_array($values) || !array_is_list($values)) fail("$file: $id $field must be a string array");
            foreach ($values as $value) {
                if (!is_string($value) || trim($value) === '') fail("$file: $id $field contains an invalid value");
            }
        }
        if (isset($item['suggestions']) && !is_string($item['suggestions'])) fail("$file: $id suggestions must be text");
        // Cana's keep-or-uninstall verdict; Canta and UAD-NG ignore it.
        if (isset($item['verdict'])) {
            $verdict = $item['verdict'];
            if (!is_array($verdict) || !in_array($verdict['action'] ?? null, ['Keep', 'Uninstall'], true)
                || !is_array($verdict['benefits'] ?? null) || !array_is_list($verdict['benefits'])
                || array_diff($verdict['benefits'], ['privacy', 'performance'])
                || !is_string($verdict['reason'] ?? null) || trim($verdict['reason']) === '') fail("$file: $id has an invalid verdict");
        }
        if (isset($uad[$id])) $replaced++; else $added++;
        // The overlay decides the rating, description and source; upstream's display name,
        // warning, references and replacement records stay unless the overlay supplies its own.
        $kept = array_intersect_key($uad[$id] ?? [], array_flip(['label', 'warning', 'web', 'suggestions', 'alternatives']));
        $uad[$id] = array_intersect_key($item, array_flip(['list', 'description', 'source', 'dependencies', 'neededBy', 'labels', 'suggestions', 'verdict'])) + $kept;
        $uad[$id]['removal'] = $item['removal'];
    }
    note(basename($file) . ": $replaced existing entries reviewed, $added new entries");
}

ksort($uad, SORT_STRING);
$encoded = json_encode($uad, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR) . "\n";
$temporary = tempnam(dirname($out), '.uad-');
if ($temporary === false) fail('cannot create output temporary file');
try {
    if (file_put_contents($temporary, $encoded) !== strlen($encoded)) fail('cannot write complete output');
    if (!chmod($temporary, 0644)) fail('cannot set output read permissions');
    if (!rename($temporary, $out)) fail('cannot replace output');
} finally {
    if (is_file($temporary)) unlink($temporary);
}
echo count($uad) . " packages written to $out\n";
$finalCounts = [];
foreach ($uad as $entry) $finalCounts[$entry['source']] = ($finalCounts[$entry['source']] ?? 0) + 1;
foreach ($finalCounts as $source => $count) echo "  $count from $source\n";
