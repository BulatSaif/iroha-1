/**
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#include "integration/acceptance/acceptance_fixture.hpp"

#include <boost/algorithm/string.hpp>
#include "backend/protobuf/transaction.hpp"
#include "cryptography/crypto_provider/crypto_defaults.hpp"
#include "datetime/time.hpp"
#include "framework/integration_framework/integration_test_framework.hpp"
#include "interfaces/permissions.hpp"
#include "module/shared_model/builders/protobuf/test_transaction_builder.hpp"

using namespace shared_model;
using namespace shared_model::crypto;
using namespace integration_framework;

struct HexKeys : public AcceptanceFixture {
  IntegrationTestFramework itf;
  HexKeys() : itf(1), kNow(iroha::time::now()) {}

  void SetUp() override {
    using Role = interface::permissions::Role;
    const interface::RolePermissionSet permissions = {Role::kAddSignatory,
                                                      Role::kRemoveSignatory,
                                                      Role::kAddPeer,
                                                      Role::kCreateAccount,
                                                      Role::kAppendRole};

    itf.setInitialState(common_constants::kAdminKeypair)
        .sendTxAwait(AcceptanceFixture::makeUserWithPerms(permissions),
                     CHECK_TXS_QUANTITY(1));
  }

  auto addSignatory(
      std::string key,
      interface::types::TimestampType time,
      interface::types::AccountIdType user_id = common_constants::kUserId) {
    return AcceptanceFixture::baseTx().createdTime(time).addSignatoryRaw(
        user_id, key);
  }

  auto removeSignatory(
      std::string key,
      interface::types::TimestampType time,
      interface::types::AccountIdType user_id = common_constants::kUserId) {
    return AcceptanceFixture::baseTx().createdTime(time).removeSignatoryRaw(
        user_id, key);
  }

  auto createAccount(std::string key, interface::types::TimestampType time) {
    return AcceptanceFixture::baseTx().createdTime(time).createAccountRaw(
        common_constants::kAnotherUser, common_constants::kDomain, key);
  }

  auto addPeer(std::string key, interface::types::TimestampType time) {
    const auto imaginary_address = "192.168.23.149:50051";
    return AcceptanceFixture::baseTx().createdTime(time).addPeerRaw(
        imaginary_address, key);
  }

  auto composeKeypairFromHex(std::string public_key, std::string private_key) {
    return crypto::Keypair(
        crypto::PublicKey(crypto::Blob::fromHexString(public_key)),
        crypto::PrivateKey(crypto::Blob::fromHexString(private_key)));
  }

  Keypair keypair = DefaultCryptoAlgorithmType::generateKeypair();
  Keypair anotherKeypair = DefaultCryptoAlgorithmType::generateKeypair();

  const std::string kLowercasedPublicKey = [this]() {
    std::string result = keypair.publicKey().hex();
    std::transform(result.begin(), result.end(), result.begin(), [](char c) {
      return std::tolower(c);
    });

    return result;
  }();

  const std::string kUppercasedPublicKey = [this]() {
    std::string result = keypair.publicKey().hex();
    std::transform(result.begin(), result.end(), result.begin(), [](char c) {
      return std::toupper(c);
    });

    return result;
  }();

  const std::string kPrivateKey = keypair.privateKey().hex();

  const std::string kAnotherPublicKey = anotherKeypair.publicKey().hex();
  const std::string kAnotherPrivateKey = anotherKeypair.privateKey().hex();

  const interface::types::TimestampType kNow;
};

/**
 * @given an account with kAddSignatory permission
 * @when the same public key is used twice but written in different case
 * @then only first attempt to add the key succeeds
 */
TEST_F(HexKeys, AddSignatory) {
  auto tx1 = complete(addSignatory(kLowercasedPublicKey, kNow));
  auto tx2 = complete(addSignatory(kUppercasedPublicKey, kNow + 1));
  auto hash1 = tx1.hash();
  auto hash2 = tx2.hash();

  itf.sendTx(tx1)
      .checkStatus(hash1, CHECK_STATELESS_VALID)
      .checkStatus(hash1, CHECK_ENOUGH_SIGNATURES)
      .checkStatus(hash1, CHECK_STATEFUL_VALID)
      .checkStatus(hash1, CHECK_COMMITTED)
      .sendTx(tx2)
      .checkStatus(hash2, CHECK_STATELESS_VALID)
      .checkStatus(hash2, CHECK_ENOUGH_SIGNATURES)
      .checkStatus(hash2, CHECK_STATEFUL_INVALID)
      .checkStatus(hash2, CHECK_REJECTED);
}

/**
 * The same as the previous test, but the keys are swapped.
 * Thus we ensure that there is no difference what case of the key is used
 * first.
 *
 * @given an account with kAddSignatory permission
 * @when the same public key is used twice but written in different case
 * @then only first attempt to add the key succeeds
 */
TEST_F(HexKeys, AddSignatoryReverse) {
  auto tx1 = complete(addSignatory(kUppercasedPublicKey, kNow));
  auto tx2 = complete(addSignatory(kLowercasedPublicKey, kNow + 1));
  auto hash1 = tx1.hash();
  auto hash2 = tx2.hash();

  itf.sendTx(tx1)
      .checkStatus(hash1, CHECK_STATELESS_VALID)
      .checkStatus(hash1, CHECK_ENOUGH_SIGNATURES)
      .checkStatus(hash1, CHECK_STATEFUL_VALID)
      .checkStatus(hash1, CHECK_COMMITTED)
      .sendTx(tx2)
      .checkStatus(hash2, CHECK_STATELESS_VALID)
      .checkStatus(hash2, CHECK_ENOUGH_SIGNATURES)
      .checkStatus(hash2, CHECK_STATEFUL_INVALID)
      .checkStatus(hash2, CHECK_REJECTED);
}

/**
 * @given a user with kAddSignatory and kRemoveSignatory permissions
 * @when a user adds a signatory using uppercased key string
 * @then the signatory can be removed using lowercased key string
 */
TEST_F(HexKeys, RemoveSignatoryUl) {
  auto tx1 = complete(addSignatory(kUppercasedPublicKey, kNow));
  auto tx2 = complete(removeSignatory(kLowercasedPublicKey, kNow + 1));
  auto hash2 = tx2.hash();

  itf.sendTxAwait(tx1, CHECK_TXS_QUANTITY(1))
      .sendTx(tx2)
      .checkStatus(hash2, CHECK_STATELESS_VALID)
      .checkStatus(hash2, CHECK_ENOUGH_SIGNATURES)
      .checkStatus(hash2, CHECK_STATEFUL_VALID)
      .checkStatus(hash2, CHECK_COMMITTED);
}

/**
 * @given a user with kAddSignatory and kRemoveSignatory permissions
 * @when a user adds a signatory using lowercased key string
 * @then the signatory can be removed using uppercased key string
 */
TEST_F(HexKeys, RemoveSignatorylU) {
  auto tx1 = complete(addSignatory(kLowercasedPublicKey, kNow));
  auto tx2 = complete(removeSignatory(kUppercasedPublicKey, kNow + 1));
  auto hash2 = tx2.hash();

  itf.sendTxAwait(tx1, CHECK_TXS_QUANTITY(1))
      .sendTx(tx2)
      .checkStatus(hash2, CHECK_STATELESS_VALID)
      .checkStatus(hash2, CHECK_ENOUGH_SIGNATURES)
      .checkStatus(hash2, CHECK_STATEFUL_VALID)
      .checkStatus(hash2, CHECK_COMMITTED);
}

/**
 * @given a user created with uppercased public key
 * @when some additional key is added to the user
 * @then the first key can be removed even when it passed in lower case to a
 * command
 */
TEST_F(HexKeys, CreateAccountUl) {
  auto user = common_constants::kSameDomainUserId;
  auto keypair = composeKeypairFromHex(kLowercasedPublicKey, kPrivateKey);

  // kUserId creates kSameDomainUserId and appends the role with test
  // permissions
  auto tx1 = complete(createAccount(kUppercasedPublicKey, kNow)
                          .appendRole(user, common_constants::kRole));

  // kSameDomainUserId adds one more key to own account
  auto tx2 = complete(
      addSignatory(kAnotherPublicKey, kNow + 1, user).creatorAccountId(user),
      keypair);

  // kSameDomainUserId removes the initial key specifing it in other font case
  auto tx3 = complete(removeSignatory(kLowercasedPublicKey, kNow + 2, user)
                          .creatorAccountId(user),
                      keypair);

  itf.sendTxAwait(tx1, CHECK_TXS_QUANTITY(1))
      .sendTxAwait(tx2, CHECK_TXS_QUANTITY(1))
      .sendTxAwait(tx3, CHECK_TXS_QUANTITY(1));
}

/**
 * The same as the previous test, but the keys are swapped.
 * Thus we ensure that there is no difference what case of the key is used
 * first.
 *
 * @given a user created with uppercased public key
 * @when some additional key is added to the user
 * @then the first key can be removed even when it passed in lower case to a
 * command
 */
TEST_F(HexKeys, CreateAccountlU) {
  auto user = common_constants::kSameDomainUserId;
  auto keypair = composeKeypairFromHex(kUppercasedPublicKey, kPrivateKey);

  // kUserId creates kSameDomainUserId and appends the role with test
  // permissions
  auto tx1 = complete(createAccount(kLowercasedPublicKey, kNow)
                          .appendRole(user, common_constants::kRole));

  // kSameDomainUserId adds one more key to own account
  auto tx2 = complete(
      addSignatory(kAnotherPublicKey, kNow + 1, user).creatorAccountId(user),
      keypair);

  // kSameDomainUserId removes the initial key specifing it in other font
  // case
  auto tx3 = complete(removeSignatory(kUppercasedPublicKey, kNow + 2, user)
                          .creatorAccountId(user),
                      keypair);

  itf.sendTxAwait(tx1, CHECK_TXS_QUANTITY(1))
      .sendTxAwait(tx2, CHECK_TXS_QUANTITY(1))
      .sendTxAwait(tx3, CHECK_TXS_QUANTITY(1));
}

/**
 * @given an initialized peer
 * @when a user tries to add another peer with the same key as the first peer
 * has, but written in a different font case
 * @then the transaction is considered as stateful invalid
 */
TEST_F(HexKeys, AddPeerSameKeyDifferentCase) {
  std::string original_key = common_constants::kAdminKeypair.publicKey().hex();
  std::string same_key_uppercased = original_key;
  boost::to_upper(same_key_uppercased);
  ASSERT_NE(original_key, same_key_uppercased);
  auto tx = complete(addPeer(same_key_uppercased, kNow));
  auto hash = tx.hash();

  itf.sendTx(tx)
      .checkStatus(hash, CHECK_STATELESS_VALID)
      .checkStatus(hash, CHECK_ENOUGH_SIGNATURES)
      .checkStatus(hash, CHECK_STATEFUL_INVALID)
      .checkStatus(hash, CHECK_REJECTED);
}
