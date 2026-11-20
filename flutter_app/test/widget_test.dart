import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:edulens/main.dart';

void main() {
  testWidgets('shows the sign-in screen when logged out', (tester) async {
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(const EduLensApp());
    expect(find.text('EduLens'), findsOneWidget);
    expect(find.text('Sign in'), findsOneWidget);
  });
}
