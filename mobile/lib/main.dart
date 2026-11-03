import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:uuid/uuid.dart';

import 'api.dart';

const blue = Color(0xff0052ff);
const violet = Color(0xff8454b5);
const categories = [
  'ELECTRONICS',
  'HOME',
  'FASHION',
  'SPORTS',
  'BOOKS',
  'OTHER',
];
const conditions = ['NEW', 'LIKE_NEW', 'GOOD', 'FAIR', 'USED', 'BAD', 'VERY_BAD'];
final uuid = Uuid();
String label(Object? value) =>
    (value ?? '').toString().replaceAll('_', ' ').toLowerCase();
String money(dynamic value, [String currency = 'CAD']) =>
    value == null ? 'Price not set' : '\$${value.toString()} $currency';

void main() => runApp(const LoopMarket());

class LoopMarket extends StatelessWidget {
  const LoopMarket({super.key, this.api});
  final Api? api;
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'LoopMarket',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      colorScheme: ColorScheme.fromSeed(
        seedColor: blue,
        surface: const Color(0xfffbf8ff),
      ),
      scaffoldBackgroundColor: const Color(0xfffbf8ff),
      useMaterial3: true,
      appBarTheme: const AppBarTheme(
        backgroundColor: Color(0xfffbf8ff),
        toolbarHeight: 52,
        titleSpacing: 16,
      ),
      visualDensity: VisualDensity.compact,
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: Colors.white,
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
      ),
    ),
    home: Home(api: api ?? Api()),
  );
}

class Home extends StatefulWidget {
  const Home({super.key, required this.api});
  final Api api;
  @override
  State<Home> createState() => _HomeState();
}

class _HomeState extends State<Home> {
  int tab = 0;
  int revision = 0;
  String? name;
  Api get api => widget.api;

  void refresh() => setState(() => revision++);
  Future<bool> signIn() async {
    if (api.token != null) return true;
    final result = await Navigator.of(
      context,
    ).push<bool>(MaterialPageRoute(builder: (_) => Login(api: api)));
    if (result == true && mounted) {
      try {
        final user = await api.request('/users/me');
        if (mounted) setState(() => name = user['displayName']);
      } catch (_) {
        if (mounted) setState(() => name = 'Account');
      }
      return true;
    }
    return false;
  }

  Future<void> editor([Map<String, dynamic>? item]) async {
    if (!await signIn() || !mounted) return;
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => Editor(api: api, initial: item),
      ),
    );
    if (mounted) refresh();
  }

  Future<void> selectTab(int value) async {
    if (value == 1 && !await signIn()) return;
    if (mounted) setState(() => tab = value);
  }

  Future<void> profile() async {
    if (api.token == null) {
      await signIn();
      return;
    }
    if (!context.mounted) return;
    final logout = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Signed in as ${name ?? 'user'}'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Sign out'),
          ),
        ],
      ),
    );
    if (logout == true) {
      try {
        await api.request('/sessions', method: 'DELETE');
        if (mounted) {
          setState(() {
            api.token = null;
            name = null;
            tab = 0;
          });
        }
      } catch (e) {
        if (mounted) showError(context, e);
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(5),
            decoration: BoxDecoration(
              color: blue,
              borderRadius: BorderRadius.circular(9),
            ),
            child: const Icon(
              Icons.all_inclusive,
              color: Colors.white,
              size: 18,
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              tab == 0 ? 'Browse Catalog' : 'My Listings',
              style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 17),
            ),
          ),
        ],
      ),
      actions: [
        IconButton(
          tooltip: name ?? 'Sign in',
          icon: const CircleAvatar(
            backgroundColor: blue,
            child: Icon(Icons.person, color: Colors.white),
          ),
          onPressed: profile,
        ),
      ],
    ),
    body: tab == 0
        ? Catalog(api: api, signIn: signIn, revision: revision)
        : Dashboard(
            api: api,
            revision: revision,
            edit: editor,
            refresh: refresh,
          ),
    floatingActionButton: tab == 0
        ? FloatingActionButton.extended(
            onPressed: editor,
            backgroundColor: blue,
            foregroundColor: Colors.white,
            icon: const Icon(Icons.add, size: 20),
            label: const Text(
              'Post item',
              style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700),
            ),
          )
        : null,
    bottomNavigationBar: SafeArea(
      top: false,
      child: Container(
        height: 62,
        decoration: const BoxDecoration(
          color: Color(0xfffbf8ff),
          boxShadow: [BoxShadow(color: Color(0x0d1d1f2a), blurRadius: 12)],
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            _navItem(
              Icons.storefront_outlined,
              'Browse',
              tab == 0,
              () => selectTab(0),
            ),
            _navItem(Icons.add_circle, 'Sell', false, editor, prominent: true),
            _navItem(
              Icons.loyalty_outlined,
              'Activity',
              tab == 1,
              () => selectTab(1),
            ),
            _navItem(Icons.account_circle_outlined, 'Profile', false, profile),
          ],
        ),
      ),
    ),
  );
}

Widget _navItem(
  IconData icon,
  String title,
  bool selected,
  VoidCallback action, {
  bool prominent = false,
}) => InkWell(
  onTap: action,
  borderRadius: BorderRadius.circular(12),
  child: SizedBox(
    width: 76,
    child: Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(
          icon,
          size: prominent ? 30 : 22,
          color: selected || prominent ? blue : const Color(0xff545969),
        ),
        const SizedBox(height: 2),
        Text(
          title,
          style: TextStyle(
            fontSize: 10,
            fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
            color: selected ? blue : const Color(0xff545969),
          ),
        ),
      ],
    ),
  ),
);

void showError(BuildContext context, Object error) => ScaffoldMessenger.of(
  context,
).showSnackBar(SnackBar(content: Text(error.toString())));

class Login extends StatefulWidget {
  const Login({super.key, required this.api});
  final Api api;
  @override
  State<Login> createState() => _LoginState();
}

class _LoginState extends State<Login> {
  final username = TextEditingController();
  final password = TextEditingController();
  bool busy = false;
  @override
  void dispose() {
    username.dispose();
    password.dispose();
    super.dispose();
  }

  Future<void> submit() async {
    setState(() => busy = true);
    try {
      final result = await widget.api.request(
        '/sessions',
        method: 'POST',
        body: {'username': username.text.trim(), 'password': password.text},
      );
      widget.api.token = result['accessToken'];
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      if (mounted) showError(context, e);
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Sign in')),
    body: ListView(
      padding: const EdgeInsets.all(20),
      children: [
        const Text(
          'Welcome back to the loop.',
          style: TextStyle(fontSize: 26, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 24),
        TextField(
          controller: username,
          autofillHints: const [AutofillHints.username],
          decoration: const InputDecoration(labelText: 'Username'),
        ),
        const SizedBox(height: 16),
        TextField(
          controller: password,
          obscureText: true,
          autofillHints: const [AutofillHints.password],
          decoration: const InputDecoration(labelText: 'Password'),
          onSubmitted: (_) => busy ? null : submit(),
        ),
        const SizedBox(height: 20),
        FilledButton(
          onPressed: busy ? null : submit,
          child: Text(busy ? 'Signing in...' : 'Sign in'),
        ),
      ],
    ),
  );
}

class Catalog extends StatefulWidget {
  const Catalog({
    super.key,
    required this.api,
    required this.signIn,
    required this.revision,
  });
  final Api api;
  final Future<bool> Function() signIn;
  final int revision;
  @override
  State<Catalog> createState() => _CatalogState();
}

class _CatalogState extends State<Catalog> {
  final search = TextEditingController();
  String category = '';
  Map<String, String> filters = {};
  int page = 1;
  late Future<dynamic> future;
  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void didUpdateWidget(covariant Catalog oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.revision != widget.revision) load();
  }

  @override
  void dispose() {
    search.dispose();
    super.dispose();
  }

  void load() {
    final query = <String, String>{
      'page': '$page',
      'size': '10',
      if (search.text.trim().isNotEmpty) 'text': search.text.trim(),
      if (category.isNotEmpty) 'category': category,
      ...filters,
    };
    future = widget.api.request(
      '/catalog?${Uri(queryParameters: query).query}',
    );
  }

  void update() => setState(load);
  Future<void> showFilters() async {
    final result = await showModalBottomSheet<Map<String, String>>(
      context: context,
      isScrollControlled: true,
      builder: (_) => FilterSheet(initial: filters),
    );
    if (result != null && mounted) {
      setState(() {
        filters = result;
        page = 1;
        load();
      });
    }
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<dynamic>(
    future: future,
    builder: (context, snapshot) {
      final data = snapshot.data;
      final items = (data?['content'] as List?) ?? [];
      return ListView(
        padding: const EdgeInsets.fromLTRB(14, 8, 14, 92),
        children: [
          TextField(
            controller: search,
            onSubmitted: (_) {
              page = 1;
              update();
            },
            style: const TextStyle(fontSize: 13),
            decoration: InputDecoration(
              hintText: 'Search the marketplace',
              prefixIcon: const Icon(
                Icons.search,
                size: 20,
                color: Color(0xff737688),
              ),
              suffixIcon: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  IconButton(
                    tooltip: 'Filters',
                    onPressed: showFilters,
                    icon: Icon(
                      Icons.tune,
                      size: 19,
                      color: filters.isEmpty ? const Color(0xff545969) : blue,
                    ),
                  ),
                  IconButton(
                    tooltip: 'Search',
                    onPressed: () {
                      page = 1;
                      update();
                    },
                    icon: const Icon(Icons.arrow_forward, size: 19),
                  ),
                ],
              ),
              filled: true,
              fillColor: const Color(0xffededfc),
              contentPadding: const EdgeInsets.symmetric(vertical: 10),
              isDense: true,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: BorderSide.none,
              ),
            ),
          ),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
            decoration: BoxDecoration(
              color: const Color(0xffededfc),
              borderRadius: BorderRadius.circular(20),
            ),
            child: const Row(
              children: [
                Icon(Icons.circle, size: 7, color: Color(0xff39618a)),
                SizedBox(width: 6),
                Expanded(
                  child: Text(
                    'Explore pre-loved finds',
                    style: TextStyle(
                      fontSize: 10,
                      color: Color(0xff39618a),
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                Icon(Icons.recycling, size: 15, color: Color(0xff39618a)),
                SizedBox(width: 4),
                Text(
                  'Keep it in the loop',
                  style: TextStyle(fontSize: 10, color: Color(0xff39618a)),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          SizedBox(
            height: 34,
            child: ListView(
              scrollDirection: Axis.horizontal,
              children: ['', ...categories].map((c) {
                final selected = category == c;
                final icons = {
                  '': Icons.grid_view_rounded,
                  'ELECTRONICS': Icons.devices_outlined,
                  'HOME': Icons.chair_outlined,
                  'FASHION': Icons.checkroom,
                  'SPORTS': Icons.sports_soccer,
                  'BOOKS': Icons.menu_book,
                  'OTHER': Icons.category_outlined,
                };
                return Padding(
                  padding: const EdgeInsets.only(right: 7),
                  child: Material(
                    color: selected ? blue : const Color(0xffededfc),
                    borderRadius: BorderRadius.circular(20),
                    child: InkWell(
                      borderRadius: BorderRadius.circular(20),
                      onTap: () {
                        category = c;
                        page = 1;
                        update();
                      },
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              icons[c],
                              size: 15,
                              color: selected
                                  ? Colors.white
                                  : const Color(0xff545969),
                            ),
                            const SizedBox(width: 5),
                            Text(
                              c.isEmpty ? 'All items' : label(c),
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.w600,
                                color: selected
                                    ? Colors.white
                                    : const Color(0xff545969),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              const Text(
                'Curated Feed',
                style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800),
              ),
              const SizedBox(width: 8),
              if (data != null)
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 3,
                  ),
                  decoration: BoxDecoration(
                    color: const Color(0xffd0e4ff),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    '${data['total']} items',
                    style: const TextStyle(
                      fontSize: 10,
                      color: Color(0xff305981),
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 10),
          if (snapshot.connectionState != ConnectionState.done)
            const Center(child: CircularProgressIndicator())
          else if (snapshot.hasError)
            ErrorState(error: snapshot.error!, retry: update)
          else if (items.isEmpty)
            const Padding(
              padding: EdgeInsets.all(30),
              child: Center(child: Text('No listings found.')),
            )
          else
            GridView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: items.length,
              gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                maxCrossAxisExtent: 260,
                crossAxisSpacing: 10,
                mainAxisSpacing: 10,
                childAspectRatio: .67,
              ),
              itemBuilder: (context, i) {
                final item = Map<String, dynamic>.from(items[i]);
                return ListingCard(
                  api: widget.api,
                  item: item,
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => Detail(
                        api: widget.api,
                        item: item,
                        signIn: widget.signIn,
                      ),
                    ),
                  ),
                );
              },
            ),
          if (data != null && data['total'] > data['size'])
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                TextButton(
                  onPressed: page > 1
                      ? () {
                          page--;
                          update();
                        }
                      : null,
                  child: const Text('Previous'),
                ),
                Text('Page $page'),
                TextButton(
                  onPressed:
                      page * (data['size'] as int) < (data['total'] as int)
                      ? () {
                          page++;
                          update();
                        }
                      : null,
                  child: const Text('Next'),
                ),
              ],
            ),
        ],
      );
    },
  );
}

class FilterSheet extends StatefulWidget {
  const FilterSheet({super.key, required this.initial});
  final Map<String, String> initial;
  @override
  State<FilterSheet> createState() => _FilterSheetState();
}

class _FilterSheetState extends State<FilterSheet> {
  final form = GlobalKey<FormState>();
  late final TextEditingController city;
  late final TextEditingController minPrice;
  late final TextEditingController maxPrice;
  late String condition;
  @override
  void initState() {
    super.initState();
    city = TextEditingController(text: widget.initial['city']);
    minPrice = TextEditingController(text: widget.initial['minPrice']);
    maxPrice = TextEditingController(text: widget.initial['maxPrice']);
    condition = widget.initial['condition'] ?? '';
  }

  @override
  void dispose() {
    city.dispose();
    minPrice.dispose();
    maxPrice.dispose();
    super.dispose();
  }

  String? priceError(String? value) {
    if (value == null || value.trim().isEmpty) return null;
    final text = value.trim();
    final price = num.tryParse(text);
    if (!RegExp(r'^\d+(\.\d{1,2})?$').hasMatch(text) ||
        price == null ||
        price < 0 ||
        price >= 10000000000) {
      return 'Enter a valid price';
    }
    return null;
  }

  void apply() {
    if (!form.currentState!.validate()) return;
    final min = num.tryParse(minPrice.text.trim());
    final max = num.tryParse(maxPrice.text.trim());
    if (min != null && max != null && min > max) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Minimum price must not exceed maximum price.'),
        ),
      );
      return;
    }
    Navigator.pop(context, <String, String>{
      if (city.text.trim().isNotEmpty) 'city': city.text.trim(),
      if (condition.isNotEmpty) 'condition': condition,
      if (min != null) 'minPrice': minPrice.text.trim(),
      if (max != null) 'maxPrice': maxPrice.text.trim(),
    });
  }

  @override
  Widget build(BuildContext context) => SafeArea(
    child: Padding(
      padding: EdgeInsets.fromLTRB(
        20,
        20,
        20,
        MediaQuery.viewInsetsOf(context).bottom + 20,
      ),
      child: Form(
        key: form,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text(
                'Filter listings',
                style: TextStyle(fontSize: 19, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: city,
                decoration: const InputDecoration(labelText: 'City'),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                initialValue: condition,
                decoration: const InputDecoration(labelText: 'Condition'),
                items: ['', ...conditions]
                    .map(
                      (value) => DropdownMenuItem(
                        value: value,
                        child: Text(
                          value.isEmpty ? 'Any condition' : label(value),
                        ),
                      ),
                    )
                    .toList(),
                onChanged: (value) => setState(() => condition = value ?? ''),
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: TextFormField(
                      controller: minPrice,
                      validator: priceError,
                      keyboardType: const TextInputType.numberWithOptions(
                        decimal: true,
                      ),
                      decoration: const InputDecoration(labelText: 'Min price'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: TextFormField(
                      controller: maxPrice,
                      validator: priceError,
                      keyboardType: const TextInputType.numberWithOptions(
                        decimal: true,
                      ),
                      decoration: const InputDecoration(labelText: 'Max price'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 18),
              FilledButton(
                onPressed: apply,
                child: const Text('Apply filters'),
              ),
              TextButton(
                onPressed: () => Navigator.pop(context, <String, String>{}),
                child: const Text('Clear filters'),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

class ListingCard extends StatelessWidget {
  const ListingCard({
    super.key,
    required this.api,
    required this.item,
    required this.onTap,
  });
  final Api api;
  final Map<String, dynamic> item;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Card(
    clipBehavior: Clip.antiAlias,
    elevation: 0,
    margin: EdgeInsets.zero,
    color: Colors.white,
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(13)),
    child: InkWell(
      onTap: onTap,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Stack(
              fit: StackFit.expand,
              children: [
                Image.network(
                  api.photo(item['id']),
                  fit: BoxFit.contain,
                  errorBuilder: (_, __, ___) => const ColoredBox(
                    color: Color(0xffededfc),
                    child: Center(child: Icon(Icons.image_outlined, size: 36)),
                  ),
                ),
                if ((item['condition'] ?? '').toString().isNotEmpty)
                  Positioned(
                    left: 8,
                    bottom: 8,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 7,
                        vertical: 3,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xc01d1f2a),
                        borderRadius: BorderRadius.circular(5),
                      ),
                      child: Text(
                        label(item['condition']),
                        style: const TextStyle(
                          fontSize: 10,
                          color: Colors.white,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(8, 7, 8, 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  money(item['price'], item['currency'] ?? 'CAD'),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: blue,
                    fontWeight: FontWeight.w800,
                    fontSize: 15,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  item['title'] ?? 'Untitled',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 12,
                  ),
                ),
                const SizedBox(height: 5),
                Row(
                  children: [
                    const Icon(
                      Icons.near_me_outlined,
                      size: 12,
                      color: Color(0xff737688),
                    ),
                    const SizedBox(width: 3),
                    Expanded(
                      child: Text(
                        item['city'] ?? '',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: Color(0xff545969),
                          fontSize: 10,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 5),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 6,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    color: const Color(0xfff3f2ff),
                    borderRadius: BorderRadius.circular(7),
                  ),
                  child: Text(
                    item['sellerDisplayName'] ?? 'Seller',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Color(0xff39618a),
                      fontSize: 10,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    ),
  );
}

class ErrorState extends StatelessWidget {
  const ErrorState({super.key, required this.error, required this.retry});
  final Object error;
  final VoidCallback retry;
  @override
  Widget build(BuildContext context) => Column(
    children: [
      Text(error.toString()),
      TextButton(onPressed: retry, child: const Text('Try again')),
    ],
  );
}

class Detail extends StatelessWidget {
  const Detail({
    super.key,
    required this.api,
    required this.item,
    required this.signIn,
  });
  final Api api;
  final Map<String, dynamic> item;
  final Future<bool> Function() signIn;
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Listing details')),
    body: ListView(
      padding: const EdgeInsets.all(16),
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(20),
          child: AspectRatio(
            aspectRatio: 1.2,
            child: FutureBuilder<dynamic>(
              future: api.request('/catalog/${item['id']}/photos'),
              builder: (context, snapshot) {
                final ids = snapshot.data is List ? snapshot.data as List : [];
                if (ids.isEmpty) return Image.network(api.photo(item['id']), fit: BoxFit.contain, errorBuilder: (_, __, ___) => const Icon(Icons.image_outlined, size: 72));
                return PageView(children: ids.map((id) => Image.network('$apiBase/catalog/${item['id']}/photos/$id', fit: BoxFit.contain, errorBuilder: (_, __, ___) => const Icon(Icons.image_outlined, size: 72))).toList());
              },
            ),
          ),
        ),
        const SizedBox(height: 20),
        Text(
          money(item['price'], item['currency'] ?? 'CAD'),
          style: const TextStyle(
            color: blue,
            fontSize: 25,
            fontWeight: FontWeight.bold,
          ),
        ),
        Text(
          item['title'] ?? '',
          style: const TextStyle(fontSize: 26, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 8),
        Text(
          '${label(item['condition'])} · ${item['city'] ?? ''} · ${item['sellerDisplayName'] ?? 'Seller'}',
        ),
        const SizedBox(height: 20),
        Text(item['description'] ?? ''),
        const SizedBox(height: 24),
        FilledButton.icon(
          onPressed: () async {
            if (!await signIn() || !context.mounted) return;
            try {
              await api.request(
                '/interests',
                method: 'POST',
                body: {'listingId': item['id']},
              );
              if (context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Interest sent to the seller.')),
                );
              }
            } catch (e) {
              if (context.mounted) showError(context, e);
            }
          },
          icon: const Icon(Icons.favorite_border),
          label: const Text('I am interested'),
        ),
      ],
    ),
  );
}

class Dashboard extends StatefulWidget {
  const Dashboard({
    super.key,
    required this.api,
    required this.revision,
    required this.edit,
    required this.refresh,
  });
  final Api api;
  final int revision;
  final Future<void> Function([Map<String, dynamic>?]) edit;
  final VoidCallback refresh;
  @override
  State<Dashboard> createState() => _DashboardState();
}

class _DashboardState extends State<Dashboard> {
  String status = '';
  int page = 1;
  late Future<dynamic> future;
  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void didUpdateWidget(covariant Dashboard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.revision != widget.revision) load();
  }

  void load() => future = widget.api.request(
    '/users/me/listings?${Uri(queryParameters: {'page': '$page', 'size': '10', if (status.isNotEmpty) 'status': status}).query}',
  );
  void update() => setState(load);
  Future<void> action(Map<String, dynamic> item, String action) async {
    if (action == 'interests') {
      try {
        final buyers =
            await widget.api.request(
                  '/users/me/listings/${item['id']}/interests',
                )
                as List;
        if (!mounted) return;
        await showDialog<void>(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Interested buyers'),
            content: SizedBox(
              width: double.maxFinite,
              child: buyers.isEmpty
                  ? const Text('No interest yet.')
                  : ListView(
                      shrinkWrap: true,
                      children: buyers
                          .map(
                            (buyer) => ListTile(
                              title: Text(buyer['buyerDisplayName'] ?? 'Buyer'),
                              subtitle: Text(
                                (buyer['createdAt'] ?? '')
                                    .toString()
                                    .split('T')
                                    .first,
                              ),
                            ),
                          )
                          .toList(),
                    ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('Close'),
              ),
            ],
          ),
        );
      } catch (e) {
        if (mounted) showError(context, e);
      }
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('${action == 'archive' ? 'Archive' : 'Delete'} listing?'),
        content: Text(item['title'] ?? 'Untitled draft'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Confirm'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      if (action == 'archive') {
        await widget.api.request(
          '/listings/${item['id']}/archives',
          method: 'POST',
          body: {'expectedVersion': item['version']},
        );
      } else {
        await widget.api.request('/listings/${item['id']}', method: 'DELETE');
      }
      if (mounted) update();
    } catch (e) {
      if (mounted) showError(context, e);
    }
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<dynamic>(
    future: future,
    builder: (context, snapshot) {
      final data = snapshot.data;
      final items = (data?['content'] as List?) ?? [];
      return ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 100),
        children: [
          const Text(
            'Live Seller Hub',
            style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 5),
          const Text(
            'Manage listings and see who is interested.',
            style: TextStyle(fontSize: 11, color: Color(0xff545969)),
          ),
          const SizedBox(height: 16),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: ['', 'PUBLISHED', 'DRAFT', 'ARCHIVED']
                  .map(
                    (s) => Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: ChoiceChip(
                        label: Text(s.isEmpty ? 'All listings' : label(s)),
                        selected: status == s,
                        onSelected: (_) {
                          status = s;
                          page = 1;
                          update();
                        },
                      ),
                    ),
                  )
                  .toList(),
            ),
          ),
          const SizedBox(height: 16),
          if (snapshot.connectionState != ConnectionState.done)
            const Center(child: CircularProgressIndicator())
          else if (snapshot.hasError)
            ErrorState(error: snapshot.error!, retry: update)
          else if (items.isEmpty)
            const Padding(
              padding: EdgeInsets.all(30),
              child: Center(child: Text('No listings here yet.')),
            )
          else
            ...items.map((raw) {
              final item = Map<String, dynamic>.from(raw);
              final state = item['status'];
              return Card(
                color: Colors.white,
                elevation: 0,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
                margin: const EdgeInsets.only(bottom: 12),
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          ClipRRect(
                            borderRadius: BorderRadius.circular(9),
                            child: SizedBox(
                              width: 70,
                              height: 70,
                              child: state == 'PUBLISHED'
                                  ? Image.network(
                                      widget.api.photo(item['id']),
                                      fit: BoxFit.contain,
                                      errorBuilder: (_, __, ___) =>
                                          const ColoredBox(
                                            color: Color(0xffededfc),
                                            child: Icon(Icons.image_outlined),
                                          ),
                                    )
                                  : const ColoredBox(
                                      color: Color(0xffededfc),
                                      child: Icon(
                                        Icons.image_outlined,
                                        color: Color(0xff737688),
                                      ),
                                    ),
                            ),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 7,
                                    vertical: 3,
                                  ),
                                  decoration: BoxDecoration(
                                    color: const Color(0xffd0e4ff),
                                    borderRadius: BorderRadius.circular(10),
                                  ),
                                  child: Text(
                                    label(state),
                                    style: const TextStyle(
                                      color: Color(0xff0038b6),
                                      fontSize: 10,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                ),
                                const SizedBox(height: 5),
                                Text(
                                  item['title']?.isNotEmpty == true
                                      ? item['title']
                                      : 'Untitled draft',
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(
                                    fontWeight: FontWeight.w700,
                                    fontSize: 13,
                                  ),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  money(
                                    item['price'],
                                    item['currency'] ?? 'CAD',
                                  ),
                                  style: const TextStyle(
                                    color: blue,
                                    fontWeight: FontWeight.w800,
                                    fontSize: 15,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      if (state == 'PUBLISHED') ...[
                        const SizedBox(height: 10),
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 6,
                          ),
                          decoration: BoxDecoration(
                            color: const Color(0xfff0efff),
                            borderRadius: BorderRadius.circular(9),
                          ),
                          child: Row(
                            children: [
                              const Icon(
                                Icons.favorite_border,
                                size: 18,
                                color: violet,
                              ),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Text(
                                  '${item['interestCount'] ?? 0} buyer interests',
                                  style: const TextStyle(
                                    fontSize: 12,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ),
                              TextButton(
                                onPressed: () => action(item, 'interests'),
                                child: const Text('Interests'),
                              ),
                            ],
                          ),
                        ),
                      ],
                      Wrap(
                        spacing: 8,
                        children: [
                          if (state != 'ARCHIVED')
                            TextButton.icon(
                              onPressed: () => widget.edit(item),
                              icon: const Icon(Icons.edit_outlined, size: 16),
                              label: const Text('Edit'),
                            ),
                          if (state == 'DRAFT')
                            TextButton(
                              onPressed: () => action(item, 'delete'),
                              child: const Text('Delete'),
                            ),
                          if (state == 'PUBLISHED')
                            TextButton(
                              onPressed: () => action(item, 'archive'),
                              child: const Text('Archive'),
                            ),
                        ],
                      ),
                    ],
                  ),
                ),
              );
            }),
          if (data != null && data['total'] > data['size'])
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                TextButton(
                  onPressed: page > 1
                      ? () {
                          page--;
                          update();
                        }
                      : null,
                  child: const Text('Previous'),
                ),
                Text('Page $page'),
                TextButton(
                  onPressed:
                      page * (data['size'] as int) < (data['total'] as int)
                      ? () {
                          page++;
                          update();
                        }
                      : null,
                  child: const Text('Next'),
                ),
              ],
            ),
        ],
      );
    },
  );
}

class Editor extends StatefulWidget {
  const Editor({super.key, required this.api, this.initial});
  final Api api;
  final Map<String, dynamic>? initial;
  @override
  State<Editor> createState() => _EditorState();
}

class _EditorState extends State<Editor> {
  final fields = <String, TextEditingController>{
    for (final key in ['title', 'description', 'city', 'price'])
      key: TextEditingController(),
  };
  Map<String, dynamic>? item;
  Map<String, dynamic>? photo;
  List<Map<String, dynamic>> photos = [];
  String category = '';
  String condition = '';
  List<String> models = [];
  String? model;
  String? modelError;
  bool busy = false;
  bool generating = false;
  bool dirty = false;
  bool loading = false;
  bool uncertain = false;
  Object? openError;
  int revision = 0;
  final createKey = uuid.v4();
  final publicationKey = uuid.v4();
  Map<String, dynamic>? createPayload;
  @override
  void initState() {
    super.initState();
    loadModels();
    if (widget.initial != null) {
      loading = true;
      open();
    }
  }

  @override
  void dispose() {
    for (final field in fields.values) {
      field.dispose();
    }
    super.dispose();
  }

  void changed() {
    revision++;
    dirty = true;
  }

  Future<void> loadModels() async {
    try {
      final result = await widget.api.request('/generations/models');
      final available = (result['models'] as List).cast<String>();
      if (!mounted) return;
      setState(() {
        models = available;
        final preferred = result['defaultModel'];
        model = available.contains(preferred) ? preferred as String : (available.isEmpty ? null : available.first);
        modelError = available.isEmpty ? 'No local models are installed.' : null;
      });
    } catch (_) {
      if (mounted) setState(() => modelError = 'Could not load local models. Check that Ollama is running.');
    }
  }

  Future<void> open() async {
    try {
      final value = Map<String, dynamic>.from(
        await widget.api.request('/users/me/listings/${widget.initial!['id']}'),
      );
      final found = <Map<String, dynamic>>[];
      var page = 1;
      dynamic response;
      do {
        response = await widget.api.request('/photos?page=$page&size=50');
        for (final raw in response['content']) {
          if (raw['listingId'] == value['id']) {
            found.add(Map<String, dynamic>.from(raw));
          }
        }
        page++;
      } while ((page - 1) * response['size'] < response['total']);
      if (!mounted) return;
      setState(() {
        item = value;
        photos = found;
        photo = found.isEmpty ? null : found.first;
        for (final key in fields.keys) {
          fields[key]!.text = value[key]?.toString() ?? '';
        }
        category = value['category'] ?? '';
        condition = value['condition'] ?? '';
        loading = false;
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          loading = false;
          openError = e;
        });
      }
    }
  }

  Map<String, dynamic> payload([int? version]) => {
    'title': fields['title']!.text.trim(),
    'description': fields['description']!.text.trim(),
    'city': fields['city']!.text.trim(),
    'price': fields['price']!.text.trim().isEmpty
        ? null
        : fields['price']!.text.trim(),
    'category': category.isEmpty ? null : category,
    'condition': condition.isEmpty ? null : condition,
    'currency': item?['currency'] ?? 'CAD',
    'expectedVersion': version,
  };
  // Keep retries on the same request identity; even Sith dislike duplicate drafts.
  Future<Map<String, dynamic>> save() async {
    final savedRevision = revision;
    if (item != null) {
      final saved = Map<String, dynamic>.from(
        await widget.api.request(
          '/listings/${item!['id']}',
          method: 'PUT',
          body: payload(item!['version']),
        ),
      );
      item = saved;
      dirty = revision != savedRevision;
      return saved;
    }
    final current = payload();
    createPayload ??= current;
    item = Map<String, dynamic>.from(
      await widget.api.request(
        '/listings',
        method: 'POST',
        body: createPayload,
        key: createKey,
      ),
    );
    if (jsonEncode(current) != jsonEncode(createPayload)) {
      item = Map<String, dynamic>.from(
        await widget.api.request(
          '/listings/${item!['id']}',
          method: 'PUT',
          body: payload(item!['version']),
        ),
      );
    }
    dirty = revision != savedRevision;
    return item!;
  }

  Future<void> run(Future<void> Function() operation) async {
    if (busy) return;
    setState(() => busy = true);
    try {
      await operation();
    } catch (e) {
      if (mounted) showError(context, e);
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> upload() async {
    if (photos.length >= 5 && !photos.any((p) => p['validationStatus'] == 'PENDING')) throw Exception('You can add up to five photos.');
    final selected = await ImagePicker().pickImage(source: ImageSource.gallery);
    if (selected == null) return;
    final size = await selected.length();
    final ext = selected.name.toLowerCase();
    final type = ext.endsWith('.png')
        ? 'image/png'
        : (ext.endsWith('.jpg') || ext.endsWith('.jpeg'))
        ? 'image/jpeg'
        : '';
    if (type.isEmpty || size == 0 || size > 64 * 1024 * 1024) {
      throw Exception('Choose a non-empty JPEG or PNG up to 64 MiB.');
    }
    final bytes = await selected.readAsBytes();
    final saved = item ?? await save();
    final metadata = {
      'listingId': saved['id'],
      'mediaType': type,
      'byteSize': bytes.length,
    };
    Map<String, dynamic>? current;
    for (final candidate in photos) {
      if (candidate['validationStatus'] == 'PENDING') { current = candidate; break; }
    }
    if (current == null) {
      current = Map<String, dynamic>.from(
        await widget.api.request('/photos', method: 'POST', body: metadata),
      );
      photos.add(current);
    } else {
      current = Map<String, dynamic>.from(
        await widget.api.request(
          '/photos/${current['id']}',
          method: 'PUT',
          body: metadata,
        ),
      );
    }
    final uploaded = Map<String, dynamic>.from(
      await widget.api.request(
        '/photos/${current['id']}/content',
        method: 'PUT',
        body: {'base64': base64Encode(bytes)},
      ),
    );
    photos = photos.map((p) => p['id'] == uploaded['id'] ? uploaded : p).toList();
    photo = uploaded;
    // A replaced photo must outsmart the image cache, even on the dark side.
    await NetworkImage(widget.api.photo(saved['id'])).evict();
    if (mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Photo uploaded.')));
    }
  }

  // A slower model cannot overwrite edits made while it consults the dark side.
  Future<void> generate() async {
    final requested = revision;
    final saved = await save();
    final key = uuid.v4();
    var result = await widget.api.request(
      '/generations',
      method: 'POST',
      body: {
        'listingId': saved['id'],
        'requestKey': key,
        'inputVersion': saved['version'],
        'clientRevision': '$requested',
        'mode': 'LOCAL',
      },
    );
    result = await widget.api.request(
      '/generations/${result['id']}/execute?model=${Uri.encodeQueryComponent(model!)}',
      method: 'POST',
    );
    while (result['status'] == 'PROCESSING') {
      await Future<void>.delayed(const Duration(seconds: 2));
      result = await widget.api.request('/generations/${result['id']}');
    }
    if (!mounted) return;
    if (requested != revision) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('The draft changed. Request new suggestions.'),
        ),
      );
      return;
    }
    if (result['status'] != 'COMPLETED' && result['status'] != 'SUCCEEDED') {
      throw Exception(
        'Generation ${label(result['status'])}. You can continue editing manually.',
      );
    }
    final proposed = Map<String, dynamic>.from(result['suggestions'] ?? {});
    setState(() {
      var applied = false;
      for (final name in ['title', 'description', 'price']) {
        final value = proposed[name];
        if (value is String && value.isNotEmpty && fields[name]!.text.trim().isEmpty &&
            (name != 'price' || (item?['currency'] ?? 'CAD') == 'CAD')) {
          fields[name]!.text = value;
          applied = true;
        }
      }
      if (category.isEmpty && categories.contains(proposed['category'])) {
        category = proposed['category'];
        applied = true;
      }
      if (condition.isEmpty && conditions.contains(proposed['condition'])) {
        condition = proposed['condition'];
        applied = true;
      }
      if (applied) { revision++; dirty = true; }
    });
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Review the estimated details before saving.')));
  }

  // An uncertain response must not trigger a second march on the same listing.
  Future<void> publish() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Publish listing?'),
        content: const Text(
          'Your listing and photo will be visible to everyone.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Publish'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    await run(() async {
      final saved = await save();
      try {
        await widget.api.request(
          '/listings/${saved['id']}/publications',
          method: 'POST',
          body: {'expectedVersion': saved['version']},
          key: publicationKey,
        );
      } on ApiException catch (e) {
        if (e.status == 0 || e.status >= 500) {
          uncertain = true;
          throw Exception(
            'Publication could not be confirmed. Reopen the listing to check its status.',
          );
        }
        rethrow;
      }
      if (mounted) {
        setState(() => dirty = false);
        await WidgetsBinding.instance.endOfFrame;
        if (mounted) Navigator.pop(context);
      }
    });
  }

  Future<bool> leave() async {
    if (!dirty) return true;
    return await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Discard unsaved changes?'),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Keep editing'),
              ),
              TextButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Discard'),
              ),
            ],
          ),
        ) ??
        false;
  }

  Widget _editorField(String key, String title, {int lines = 1}) => TextField(
    controller: fields[key],
    enabled: !busy,
    onChanged: (_) => setState(changed),
    keyboardType: key == 'price'
        ? const TextInputType.numberWithOptions(decimal: true)
        : TextInputType.text,
    maxLines: lines,
    style: const TextStyle(fontSize: 13),
    decoration: InputDecoration(
      labelText: title,
      filled: true,
      fillColor: const Color(0xfff3f2ff),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: BorderSide.none,
      ),
    ),
  );

  @override
  Widget build(BuildContext context) => PopScope(
    canPop: !dirty,
    onPopInvokedWithResult: (didPop, result) async {
      if (!didPop && await leave() && context.mounted) {
        setState(() => dirty = false);
        await WidgetsBinding.instance.endOfFrame;
        if (context.mounted) Navigator.pop(context);
      }
    },
    child: Scaffold(
      appBar: AppBar(
        title: Text(item == null ? 'Create listing' : 'Edit listing'),
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : openError != null
          ? ErrorState(
              error: openError!,
              retry: () {
                setState(() {
                  loading = true;
                  openError = null;
                });
                open();
              },
            )
          : ListView(
              padding: const EdgeInsets.fromLTRB(14, 6, 14, 36),
              children: [
                Row(
                  children: [
                    const Icon(Icons.circle, size: 8, color: blue),
                    const SizedBox(width: 6),
                    Text(
                      item == null ? 'Create Listing' : 'Edit Listing',
                      style: const TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  item == null
                      ? 'Start with a photo of your item'
                      : 'Review your listing before publishing',
                  style: const TextStyle(
                    fontSize: 11,
                    color: Color(0xff545969),
                  ),
                ),
                const SizedBox(height: 14),
                ClipRRect(
                  borderRadius: BorderRadius.circular(14),
                  child: SizedBox(
                    height: 180,
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        if (photo != null &&
                            photo!['validationStatus'] == 'VALID')
                          Image.network(
                            '$apiBase/photos/${photo!['id']}/content?v=${Uri.encodeQueryComponent(photo!['updatedAt']?.toString() ?? '')}',
                            headers: {
                              'Authorization': 'Bearer ${widget.api.token}',
                            },
                            fit: BoxFit.contain,
                            errorBuilder: (_, __, ___) => const ColoredBox(
                              color: Color(0xffededfc),
                              child: Center(
                                child: Icon(
                                  Icons.image_outlined,
                                  size: 42,
                                  color: Color(0xff737688),
                                ),
                              ),
                            ),
                          )
                        else
                          const ColoredBox(
                            color: Color(0xffededfc),
                            child: Center(
                              child: Icon(
                                Icons.add_photo_alternate_outlined,
                                size: 42,
                                color: Color(0xff737688),
                              ),
                            ),
                          ),
                        Positioned(
                          right: 10,
                          bottom: 10,
                          child: FilledButton.tonalIcon(
                            onPressed: busy || item?['status'] == 'PUBLISHED' || (photos.length >= 5 && !photos.any((p) => p['validationStatus'] == 'PENDING')) ? null : () => run(upload),
                            icon: const Icon(
                              Icons.photo_camera_outlined,
                              size: 16,
                            ),
                            label: Text(
                              'Add photo (${photos.length}/5)',
                              style: const TextStyle(fontSize: 11),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                if (photos.isNotEmpty) Wrap(spacing: 8, children: photos.map((saved) => Column(mainAxisSize: MainAxisSize.min, children: [
                  InkWell(onTap: busy ? null : () => setState(() => photo = saved), child: SizedBox(width: 64, height: 64, child: saved['validationStatus'] == 'VALID' ? Image.network('$apiBase/photos/${saved['id']}/content?v=${Uri.encodeQueryComponent(saved['updatedAt']?.toString() ?? '')}', headers: {'Authorization': 'Bearer ${widget.api.token}'}, fit: BoxFit.cover, errorBuilder: (_, __, ___) => const Icon(Icons.broken_image)) : const Icon(Icons.image_outlined))),
                  TextButton(onPressed: busy || item?['status'] == 'PUBLISHED' ? null : () => run(() async {
                    await widget.api.request('/photos/${saved['id']}', method: 'DELETE');
                    setState(() { photos.removeWhere((p) => p['id'] == saved['id']); if (photo?['id'] == saved['id']) photo = photos.isEmpty ? null : photos.first; revision++; });
                  }), child: const Text('Remove')),
                ])).toList()),
                const SizedBox(height: 14),
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: const Color(0xfff0efff),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          const Icon(
                            Icons.auto_awesome,
                            color: violet,
                            size: 19,
                          ),
                          const SizedBox(width: 8),
                          const Expanded(
                            child: Text(
                              'Vision Engine',
                              style: TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 4,
                            ),
                            decoration: BoxDecoration(
                              color: const Color(0xfff0dbff),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: const Text(
                              'Local model',
                              style: const TextStyle(
                                fontSize: 10,
                                color: violet,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 5),
                      const Text(
                        'Empty fields are filled automatically. Review the estimated price and condition before saving.',
                        style: TextStyle(
                          fontSize: 11,
                          color: Color(0xff545969),
                        ),
                      ),
                      DropdownButton<String>(
                        value: model,
                        isExpanded: true,
                        hint: const Text('Select a local model'),
                        items: models.map((name) => DropdownMenuItem(value: name, child: Text(name))).toList(),
                        onChanged: busy
                            ? null
                            : (v) => setState(() => model = v),
                      ),
                      if (modelError != null) Text(modelError!),
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton.icon(
                          style: FilledButton.styleFrom(
                            backgroundColor: violet,
                          ),
                          onPressed: busy || model == null ? null : () => run(() async {
                            setState(() => generating = true);
                            try { await generate(); } finally { if (mounted) setState(() => generating = false); }
                          }),
                          icon: generating ? const SizedBox(width: 17, height: 17, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.auto_awesome, size: 17),
                          label: const Text('Generate suggestions'),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 14),
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Item Specifics',
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 14),
                      _editorField('title', 'Title'),
                      const SizedBox(height: 12),
                      DropdownButtonFormField<String>(
                        initialValue: category,
                        decoration: const InputDecoration(
                          labelText: 'Category',
                        ),
                        items: ['', ...categories]
                            .map(
                              (s) => DropdownMenuItem(
                                value: s,
                                child: Text(
                                  s.isEmpty ? 'Select category' : label(s),
                                ),
                              ),
                            )
                            .toList(),
                        onChanged: busy
                            ? null
                            : (value) => setState(() {
                                category = value!;
                                changed();
                              }),
                      ),
                      const SizedBox(height: 12),
                      DropdownButtonFormField<String>(
                        initialValue: condition,
                        decoration: const InputDecoration(
                          labelText: 'Condition',
                        ),
                        items: ['', ...conditions]
                            .map(
                              (s) => DropdownMenuItem(
                                value: s,
                                child: Text(
                                  s.isEmpty ? 'Select condition' : label(s),
                                ),
                              ),
                            )
                            .toList(),
                        onChanged: busy
                            ? null
                            : (value) => setState(() {
                                condition = value!;
                                changed();
                              }),
                      ),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          Expanded(
                            child: _editorField(
                              'price',
                              'Price (${item?['currency'] ?? 'CAD'})',
                            ),
                          ),
                          const SizedBox(width: 10),
                          Expanded(child: _editorField('city', 'City')),
                        ],
                      ),
                      const SizedBox(height: 12),
                      _editorField('description', 'Description', lines: 4),
                    ],
                  ),
                ),
                const SizedBox(height: 14),
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: const Color(0xfff0efff),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: const Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(Icons.shield_outlined, color: violet, size: 20),
                      SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Human-in-the-Loop Review',
                              style: TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                            SizedBox(height: 4),
                            Text(
                              'Review the details and any suggestions before publishing.',
                              style: TextStyle(
                                fontSize: 11,
                                color: Color(0xff545969),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 18),
                FilledButton.icon(
                  onPressed: busy || uncertain || item?['status'] == 'PUBLISHED'
                      ? null
                      : publish,
                  icon: const Icon(Icons.rocket_launch_outlined, size: 18),
                  label: Text(busy ? 'Working...' : 'Publish listing'),
                ),
                OutlinedButton(
                  onPressed: busy
                      ? null
                      : () => run(() async {
                          await save();
                          if (context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('Draft saved.')),
                            );
                          }
                        }),
                  child: const Text('Save draft'),
                ),
              ],
            ),
    ),
  );
}
